package com.claimassist.platform.agent_service.ai.tool;

import com.claimassist.platform.agent_service.config.AgentAiProperties;
import com.claimassist.platform.agent_service.observability.AgentErrorCategory;
import com.claimassist.platform.agent_service.observability.AgentTelemetry;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.lang.Nullable;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Request-scoped guard that enforces tool-call limits, per-tool timeouts and
 * captures per-execution metadata.
 * <p>
 * One instance is created per agent request (see AgentGenerationServiceImpl),
 * so the invocation counter is request-isolated and one request's tool budget
 * can never leak into another. The underlying worker pool is shared and static
 * to avoid spinning up a thread pool per request.
 * <p>
 * Every {@link ToolCallback} handed to Spring AI is wrapped by
 * {@link #guarded(ToolCallbackProvider)}, so the framework still drives the
 * model's tool calls but each one is bounded before it can hit a backend.
 * <p>
 * Timeout: the per-tool budget comes from the {@link ToolRegistry} when that
 * tool declares a specific timeout, otherwise the global
 * {@code agent.ai.tool-timeout-ms}. This is the OUTER bound on a tool call;
 * the resilient gateways apply their own inner client/Resilience4j timeouts.
 * Retry at this layer is intentionally absent - transient retries live in the
 * resilient gateways, and retrying here would duplicate/conflict with them.
 */
public class ToolExecutionGuard {

    /**
     * Shared, bounded pool that runs a single tool invocation under its
     * per-tool timeout. Kept as a small fixed pool (not one thread per request)
     * so a burst of concurrent requests cannot spawn unbounded threads; the
     * per-request counter (below) keeps any one request's budget isolated.
     * Daemon threads never prevent JVM shutdown, and the static hook below
     * drains the pool on exit so it is never left unmanaged.
     * <p>
     * The work queue is BOUNDED (Phase 7 hardening). {@code newFixedThreadPool}
     * would use an unbounded queue, letting a burst of concurrent agent
     * requests queue unbounded pending tool work. Here the queue has a finite
     * capacity: once the pool AND the queue are saturated, further tool
     * submissions are rejected fast (see {@link #execute}) and surface as a
     * controlled transient tool failure - the agent degrades gracefully instead
     * of building an ever-growing backlog of queued executions.
     */
    private static final int TOOL_POOL_SIZE = 4;
    private static final int TOOL_QUEUE_CAPACITY = 64;
    private static final ExecutorService TOOL_EXECUTOR =
            new ThreadPoolExecutor(
                    TOOL_POOL_SIZE, TOOL_POOL_SIZE, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(TOOL_QUEUE_CAPACITY),
                    r -> {
                        Thread t = new Thread(r, "agent-tool-executor");
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.AbortPolicy());

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(
                TOOL_EXECUTOR::shutdownNow, "agent-tool-executor-shutdown"));
    }

    private final AgentAiProperties properties;
    private final ToolRegistry registry;
    private final String requestId;
    private final String correlationId;
    private final Consumer<ToolExecutionMetadata> executionCollector;
    private final AgentTelemetry agentTelemetry;
    private final Long conversationId;
    private final Long userId;
    private final String claimIdHash;
    private final ExecutorService executor;
    private final AtomicInteger toolCalls = new AtomicInteger();

    /** Convenience constructor for tests that do not need execution metadata. */
    public ToolExecutionGuard(AgentAiProperties properties, ToolRegistry registry) {
        this(properties, registry, "", "", meta -> { });
    }

    public ToolExecutionGuard(AgentAiProperties properties, ToolRegistry registry,
                              String requestId, String correlationId,
                              Consumer<ToolExecutionMetadata> executionCollector) {
        this(properties, registry, requestId, correlationId, executionCollector, null, null, null, null);
    }

    public ToolExecutionGuard(AgentAiProperties properties, ToolRegistry registry,
                              String requestId, String correlationId,
                              Consumer<ToolExecutionMetadata> executionCollector,
                              @Nullable AgentTelemetry agentTelemetry,
                              Long conversationId, Long userId, String claimIdHash) {
        this(properties, registry, requestId, correlationId, executionCollector,
                agentTelemetry, conversationId, userId, claimIdHash, TOOL_EXECUTOR);
    }

    /**
     * Full constructor that also accepts the {@link ExecutorService} used to
     * run tool invocations. Production always uses the shared static
     * {@link #TOOL_EXECUTOR}; tests supply a tiny, pre-saturated executor to
     * exercise the rejection (bounded-queue) path deterministically.
     */
    ToolExecutionGuard(AgentAiProperties properties, ToolRegistry registry,
                       String requestId, String correlationId,
                       Consumer<ToolExecutionMetadata> executionCollector,
                       @Nullable AgentTelemetry agentTelemetry,
                       Long conversationId, Long userId, String claimIdHash,
                       ExecutorService executor) {
        this.properties = properties;
        this.registry = registry;
        this.requestId = requestId == null ? "" : requestId;
        this.correlationId = correlationId == null ? "" : correlationId;
        this.executionCollector = executionCollector;
        this.agentTelemetry = agentTelemetry;
        this.conversationId = conversationId;
        this.userId = userId;
        this.claimIdHash = claimIdHash == null ? "" : claimIdHash;
        this.executor = executor;
    }

    /** Wrap every callback from the provider so each call is counted + time-bounded. */
    public ToolCallbackProvider guarded(ToolCallbackProvider provider) {
        ToolCallback[] wrapped = Arrays.stream(provider.getToolCallbacks())
                .map(cb -> new GuardedToolCallback(cb, this))
                .toArray(ToolCallback[]::new);
        return ToolCallbackProvider.from(wrapped);
    }

    /** Executes the delegate within the per-tool budget, counting the invocation. */
    public String execute(String toolName, Supplier<String> delegate) {
        if (agentTelemetry != null) {
            agentTelemetry.toolRequested(requestId, correlationId, conversationId, userId, toolName, claimIdHash);
        }
        int count = toolCalls.incrementAndGet();
        if (count > properties.getMaxToolCalls()) {
            emit(toolName, ToolExecutionMetadata.STATUS_LIMIT_EXCEEDED, 0L);
            if (agentTelemetry != null) {
                agentTelemetry.toolFailed(requestId, correlationId, conversationId, userId,
                        toolName, claimIdHash, 0L, ToolExecutionMetadata.STATUS_LIMIT_EXCEEDED,
                        AgentErrorCategory.TOOL_ERROR);
            }
            throw new ToolCallLimitExceededException(properties.getMaxToolCalls());
        }

        long timeoutMs = registry.timeoutMs(toolName).orElse(properties.getToolTimeoutMs());
        long start = System.nanoTime();
        if (agentTelemetry != null) {
            agentTelemetry.toolStarted(requestId, correlationId, conversationId, userId, toolName, claimIdHash);
        }
        Future<String> future;
        try {
            future = executor.submit(delegate::get);
        } catch (RejectedExecutionException saturated) {
            // Phase 7 hardening: the bounded tool pool AND its queue are full
            // (an overload burst). Fail FAST with a controlled transient tool
            // failure rather than queueing unbounded work; the agent reports it
            // as a temporary unavailability and the caller is not held hostage
            // by a growing backlog.
            long durationMs = elapsedMs(start);
            emit(toolName, ToolExecutionMetadata.STATUS_FAILED, durationMs);
            if (agentTelemetry != null) {
                agentTelemetry.toolFailed(requestId, correlationId, conversationId, userId,
                        toolName, claimIdHash, durationMs, ToolExecutionMetadata.STATUS_FAILED,
                        AgentErrorCategory.TOOL_ERROR);
            }
            throw new ToolExecutionException(toolName, saturated);
        }
        try {
            String result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            long durationMs = elapsedMs(start);
            result = boundToolResult(result, toolName, durationMs);
            emit(toolName, ToolExecutionMetadata.STATUS_SUCCESS, durationMs);
            if (agentTelemetry != null) {
                agentTelemetry.toolCompleted(requestId, correlationId, conversationId, userId,
                        toolName, claimIdHash, durationMs, ToolExecutionMetadata.STATUS_SUCCESS);
            }
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            long durationMs = elapsedMs(start);
            emit(toolName, ToolExecutionMetadata.STATUS_TIMEOUT, durationMs);
            if (agentTelemetry != null) {
                agentTelemetry.toolFailed(requestId, correlationId, conversationId, userId,
                        toolName, claimIdHash, durationMs, ToolExecutionMetadata.STATUS_TIMEOUT,
                        AgentErrorCategory.TOOL_ERROR);
            }
            throw new ToolExecutionTimeoutException(toolName, timeoutMs);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            long durationMs = elapsedMs(start);
            if (cause instanceof RuntimeException re) {
                emit(toolName, ToolExecutionMetadata.STATUS_FAILED, durationMs);
                if (agentTelemetry != null) {
                    agentTelemetry.toolFailed(requestId, correlationId, conversationId, userId,
                            toolName, claimIdHash, durationMs, ToolExecutionMetadata.STATUS_FAILED,
                            AgentErrorCategory.TOOL_ERROR);
                }
                throw re;
            }
            emit(toolName, ToolExecutionMetadata.STATUS_FAILED, durationMs);
            if (agentTelemetry != null) {
                agentTelemetry.toolFailed(requestId, correlationId, conversationId, userId,
                        toolName, claimIdHash, durationMs, ToolExecutionMetadata.STATUS_FAILED,
                        AgentErrorCategory.TOOL_ERROR);
            }
            throw new ToolExecutionException(toolName, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long durationMs = elapsedMs(start);
            emit(toolName, ToolExecutionMetadata.STATUS_FAILED, durationMs);
            if (agentTelemetry != null) {
                agentTelemetry.toolFailed(requestId, correlationId, conversationId, userId,
                        toolName, claimIdHash, durationMs, ToolExecutionMetadata.STATUS_FAILED,
                        AgentErrorCategory.TOOL_ERROR);
            }
            throw new ToolExecutionException(toolName, e);
        }
    }

    private void emit(String toolName, String status, long durationMs) {
        executionCollector.accept(new ToolExecutionMetadata(
                requestId, correlationId, toolName, status, durationMs));
    }

    /**
     * Bound a tool result before it is fed back to the LLM context (Phase 7.9).
     * An oversized result is truncated EXPLICITLY (with a visible marker) and
     * recorded via telemetry - it is never silently dropped, and it can never
     * explode the model's context window. The head of the payload (the
     * meaningful business data) is preserved.
     */
    private String boundToolResult(String result, String toolName, long durationMs) {
        int maxChars = properties.getMaxToolResultChars();
        if (result == null || maxChars <= 0 || result.length() <= maxChars) {
            return result;
        }
        String truncated = result.substring(0, maxChars)
                + "\n...[truncated: " + (result.length() - maxChars)
                + " characters omitted - tool result exceeded configured bound]";
        if (agentTelemetry != null) {
            agentTelemetry.toolResultTruncated(requestId, correlationId, conversationId, userId,
                    toolName, result.length(), maxChars);
        }
        return truncated;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}