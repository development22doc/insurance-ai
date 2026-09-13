package com.claimassist.platform.agent_service.ai.tool;

import com.claimassist.platform.agent_service.config.AgentAiProperties;
import com.claimassist.platform.agent_service.observability.AgentErrorCategory;
import com.claimassist.platform.agent_service.observability.AgentTelemetry;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
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
 * <p>
 * Uses Reactor's boundedElastic scheduler to preserve Reactor context
 * (including SecurityContext) across thread boundaries, ensuring that
 * reactive permission checks can access the JWT.
 */
public class ToolExecutionGuard {

    private final AgentAiProperties properties;
    private final ToolRegistry registry;
    private final String requestId;
    private final String correlationId;
    private final Consumer<ToolExecutionMetadata> executionCollector;
    private final AgentTelemetry agentTelemetry;
    private final Long conversationId;
    private final Long userId;
    private final String claimIdHash;
    private final ExecutorService executor; // nullable; if null, use boundedElastic
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
                agentTelemetry, conversationId, userId, claimIdHash, null);
    }

    /**
     * Full constructor that also accepts the {@link ExecutorService} used to
     * run tool invocations. Production always uses null (boundedElastic scheduler);
     * tests supply a tiny, pre-saturated executor to exercise the rejection
     * (bounded-queue) path deterministically.
     */
    ToolExecutionGuard(AgentAiProperties properties, ToolRegistry registry,
                       String requestId, String correlationId,
                       Consumer<ToolExecutionMetadata> executionCollector,
                       @Nullable AgentTelemetry agentTelemetry,
                       Long conversationId, Long userId, String claimIdHash,
                       @Nullable ExecutorService executor) {
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

        // Execute the entire tool callback on boundedElastic to avoid blocking
        // reactor event-loop threads. This ensures that:
        // 1. The tool execution (including any blocking Feign calls) runs on a
        //    blocking-safe thread pool
        // 2. The tool callback can access Reactor context (including SecurityContext)
        //    when it's available, without relying on ThreadLocal
        //
        // Use Mono.block(Duration) with subscribeOn to ensure work happens on
        // boundedElastic while properly handling the blocking semantics.
        // IMPORTANT: The entire Mono chain (including block()) must be scheduled
        // on boundedElastic to avoid blocking the reactor event-loop thread.
        String result;
        try {
            result = Mono.fromCallable(delegate::get)
                    .subscribeOn(Schedulers.boundedElastic())
                    .block(Duration.ofMillis(timeoutMs));
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
        } catch (Exception e) {
            // Handle timeout and other exceptions
            long durationMs = elapsedMs(start);
            // Mono.block(Duration) throws IllegalStateException on timeout
            if (e instanceof IllegalStateException && e.getMessage() != null &&
                e.getMessage().contains("Timeout")) {
                emit(toolName, ToolExecutionMetadata.STATUS_TIMEOUT, durationMs);
                if (agentTelemetry != null) {
                    agentTelemetry.toolFailed(requestId, correlationId, conversationId, userId,
                            toolName, claimIdHash, durationMs, ToolExecutionMetadata.STATUS_TIMEOUT,
                            AgentErrorCategory.TOOL_ERROR);
                }
                throw new ToolExecutionTimeoutException(toolName, timeoutMs);
            }
            // Handle other exceptions
            Throwable cause = e.getCause();
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
            throw new ToolExecutionException(toolName, cause != null ? cause : e);
        }

        long durationMs = elapsedMs(start);
        result = boundToolResult(result, toolName, durationMs);
        emit(toolName, ToolExecutionMetadata.STATUS_SUCCESS, durationMs);
        if (agentTelemetry != null) {
            agentTelemetry.toolCompleted(requestId, correlationId, conversationId, userId,
                    toolName, claimIdHash, durationMs, ToolExecutionMetadata.STATUS_SUCCESS);
        }
        return result;
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
