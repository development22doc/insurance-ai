package com.claimassist.platform.agent_service.observability;

import com.claimassist.platform.agent_service.cache.CacheMetrics;
import com.claimassist.platform.agent_service.cache.CacheService;
import com.claimassist.platform.common_lib.observability.MDCUtility;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The agent's AI-observability / audit facade (Phase 6).
 * <p>
 * Reuses the platform's existing observability infrastructure rather than
 * building a new framework:
 * <ul>
 *   <li><b>Structured events</b> are emitted through the platform
 *       {@link EventLogger} (JSON to the dedicated {@code event.logger}).</li>
 *   <li><b>Latency</b> is additionally routed through the platform
 *       {@link PerformanceLogger} (threshold-classified) where a duration is
 *       captured.</li>
 *   <li><b>Metrics</b> use the already-present Micrometer {@link MeterRegistry}
 *       (Actuator + Prometheus are configured); cache hit/miss/error are exposed
 *       as gauges bound to the existing Phase 5 {@link CacheMetrics} - never
 *       duplicated.</li>
 * </ul>
 *
 * <h2>Critical guarantees</h2>
 * <ul>
 *   <li><b>Observability is NOT business logic.</b> Every method is fail-safe:
 *       a telemetry failure (null dependency, registry error, logger error) can
 *       never break or slow the business operation.</li>
 *   <li><b>Thread-safe / request-isolated.</b> The component is stateless - all
 *       correlation data is passed in per call, so concurrent requests never
 *       share or mix telemetry (verified by a concurrency test).</li>
 *   <li><b>Low-cardinality metrics only.</b> Metric labels are limited to
 *       {@code tool}, {@code result}, {@code errorCategory}, {@code model},
 *       {@code provider}. Never requestId/userId/conversationId/claimId.</li>
 *   <li><b>No PII / no secrets / no chain-of-thought.</b> Correlation ids are
 *       passed by callers; tool events carry only a hashed resource reference
 *       via {@link SafeMetadata}.</li>
 * </ul>
 */
@Service
public class AgentTelemetry {

    private static final String COMPONENT = "agent";

    private final EventLogger eventLogger;
    private final PerformanceLogger performanceLogger;
    private final MeterRegistry meterRegistry;

    public AgentTelemetry(@Nullable EventLogger eventLogger,
                          @Nullable PerformanceLogger performanceLogger,
                          @Nullable MeterRegistry meterRegistry,
                          @Nullable CacheService cacheService) {
        this.eventLogger = eventLogger;
        this.performanceLogger = performanceLogger;
        this.meterRegistry = meterRegistry;
        registerCacheGauges(cacheService);
    }

    // ------------------------------------------------------------------
    // Request / context / response lifecycle
    // ------------------------------------------------------------------

    public void requestStarted(String requestId, String correlationId, Long conversationId,
                               Long userId, String model, String provider) {
        safe(() -> {
            MDCUtility.putRequestId(requestId);
            if (correlationId != null) {
                MDCUtility.putCorrelationId(correlationId);
            }
            counter("agent.requests.total").increment();
            emitBusiness(AgentLifecycleEventType.REQUEST_STARTED, requestId, correlationId,
                    conversationId, userId, map("model", model, "provider", provider), null);
        });
    }

    public void contextBuilt(String requestId, String correlationId, Long conversationId,
                             Long userId, long durationMs, int contextMessages) {
        safe(() -> {
            recordTiming("agent.context.duration", durationMs);
            emitBusiness(AgentLifecycleEventType.CONTEXT_BUILT, requestId, correlationId,
                    conversationId, userId, map("durationMs", durationMs, "contextMessages", contextMessages),
                    durationMs);
        });
    }

    public void responseCompleted(String requestId, String correlationId, Long conversationId,
                                  Long userId, long durationMs) {
        safe(() -> {
            recordTiming("agent.request.duration", durationMs);
            emitBusiness(AgentLifecycleEventType.RESPONSE_COMPLETED, requestId, correlationId,
                    conversationId, userId, map("durationMs", durationMs), durationMs);
        });
    }

    public void responseFailed(String requestId, String correlationId, Long conversationId,
                               Long userId, long durationMs, AgentErrorCategory errorCategory) {
        safe(() -> {
            recordTiming("agent.request.duration", durationMs);
            counter("agent.requests.failed", "errorCategory", label(errorCategory)).increment();
            emitBusiness(AgentLifecycleEventType.RESPONSE_FAILED, requestId, correlationId,
                    conversationId, userId, map("durationMs", durationMs, "errorCategory", label(errorCategory)),
                    durationMs);
        });
    }

    // ------------------------------------------------------------------
    // LLM telemetry
    // ------------------------------------------------------------------

    public void llmStarted(String requestId, String correlationId, Long conversationId,
                           Long userId, String model, String provider) {
        safe(() -> {
            counter("agent.llm.requests", "model", safe(model), "provider", safe(provider)).increment();
            emitBusiness(AgentLifecycleEventType.LLM_STARTED, requestId, correlationId,
                    conversationId, userId, map("model", model, "provider", provider), null);
        });
    }

    public void llmCompleted(String requestId, String correlationId, Long conversationId, Long userId,
                             long durationMs, String finishReason, int toolCallCount,
                             Integer promptTokens, Integer completionTokens) {
        safe(() -> {
            recordTiming("agent.llm.duration", durationMs);
            counter("agent.llm.requests").increment();
            emitBusiness(AgentLifecycleEventType.LLM_COMPLETED, requestId, correlationId,
                    conversationId, userId, map("durationMs", durationMs,
                            "finishReason", finishReason,
                            "toolCallCount", toolCallCount,
                            "promptTokens", tokens(promptTokens),
                            "completionTokens", tokens(completionTokens),
                            "totalTokens", tokens(total(promptTokens, completionTokens))),
                    durationMs);
        });
    }

    public void llmFailed(String requestId, String correlationId, Long conversationId, Long userId,
                          long durationMs, AgentErrorCategory errorCategory) {
        safe(() -> {
            recordTiming("agent.llm.duration", durationMs);
            counter("agent.llm.errors", "errorCategory", label(errorCategory)).increment();
            emitBusiness(AgentLifecycleEventType.LLM_FAILED, requestId, correlationId,
                    conversationId, userId, map("durationMs", durationMs, "errorCategory", label(errorCategory)),
                    durationMs);
        });
    }

    // ------------------------------------------------------------------
    // Tool telemetry (safe metadata only)
    // ------------------------------------------------------------------

    public void toolRequested(String requestId, String correlationId, Long conversationId, Long userId,
                              String toolName, String claimIdHash) {
        safe(() -> emitBusiness(AgentLifecycleEventType.TOOL_REQUESTED, requestId, correlationId,
                conversationId, userId, map("tool", toolName, "claimIdHash", claimIdHash), null));
    }

    public void toolStarted(String requestId, String correlationId, Long conversationId, Long userId,
                            String toolName, String claimIdHash) {
        safe(() -> emitBusiness(AgentLifecycleEventType.TOOL_STARTED, requestId, correlationId,
                conversationId, userId, map("tool", toolName, "claimIdHash", claimIdHash), null));
    }

    public void toolCompleted(String requestId, String correlationId, Long conversationId, Long userId,
                              String toolName, String claimIdHash, long durationMs, String result) {
        safe(() -> {
            recordTiming("agent.tool.duration", durationMs, "tool", safe(toolName), "result", safe(result));
            counter("agent.tool.executions", "tool", safe(toolName), "result", safe(result)).increment();
            emitBusiness(AgentLifecycleEventType.TOOL_COMPLETED, requestId, correlationId,
                    conversationId, userId, map("tool", toolName, "claimIdHash", claimIdHash,
                            "durationMs", durationMs, "result", result), durationMs);
        });
    }

    public void toolFailed(String requestId, String correlationId, Long conversationId, Long userId,
                           String toolName, String claimIdHash, long durationMs, String result,
                           AgentErrorCategory errorCategory) {
        safe(() -> {
            recordTiming("agent.tool.duration", durationMs, "tool", safe(toolName), "result", safe(result));
            counter("agent.tool.executions", "tool", safe(toolName), "result", safe(result)).increment();
            counter("agent.tool.errors", "tool", safe(toolName),
                    "errorCategory", label(errorCategory)).increment();
            emitBusiness(AgentLifecycleEventType.TOOL_FAILED, requestId, correlationId,
                    conversationId, userId, map("tool", toolName, "claimIdHash", claimIdHash,
                            "durationMs", durationMs, "result", result, "errorCategory", label(errorCategory)),
                    durationMs);
        });
    }

    // ------------------------------------------------------------------
    // Security / guardrail telemetry
    // ------------------------------------------------------------------

    public void securityDenied(String requestId, String correlationId, Long conversationId, Long userId,
                               String tool, String reason) {
        safe(() -> {
            counter("agent.security.denied", "tool", safe(tool), "reason", safe(reason)).increment();
            emitSecurity(AgentLifecycleEventType.SECURITY_DENIED, requestId, correlationId,
                    conversationId, userId, map("tool", tool, "reason", reason));
        });
    }

    public void guardrailRejected(String requestId, String correlationId, Long conversationId, Long userId,
                                  String code, String message) {
        safe(() -> {
            counter("agent.security.denied", "reason", safe(code)).increment();
            emitSecurity(AgentLifecycleEventType.PROMPT_GUARDRAIL_REJECTED, requestId, correlationId,
                    conversationId, userId, map("code", code, "message", SafeMetadata.redact(message)));
        });
    }

    // ------------------------------------------------------------------
    // SSE stream telemetry
    // ------------------------------------------------------------------

    public void streamStarted(String requestId, String correlationId, Long conversationId, Long userId) {
        safe(() -> emitBusiness(AgentLifecycleEventType.STREAM_STARTED, requestId, correlationId,
                conversationId, userId, map(), null));
    }

    public void streamCompleted(String requestId, String correlationId, Long conversationId, Long userId) {
        safe(() -> emitBusiness(AgentLifecycleEventType.STREAM_COMPLETED, requestId, correlationId,
                conversationId, userId, map(), null));
    }

    public void streamFailed(String requestId, String correlationId, Long conversationId, Long userId,
                             AgentErrorCategory errorCategory) {
        safe(() -> emitBusiness(AgentLifecycleEventType.STREAM_FAILED, requestId, correlationId,
                conversationId, userId, map("errorCategory", label(errorCategory)), null));
    }

    public void streamCancelled(String requestId, String correlationId, Long conversationId, Long userId) {
        safe(() -> emitBusiness(AgentLifecycleEventType.STREAM_CANCELLED, requestId, correlationId,
                conversationId, userId, map(), null));
    }

    // ------------------------------------------------------------------
    // Reliability (Phase 7)
    // ------------------------------------------------------------------

    /**
     * Record that a tool result exceeded the configured context bound and was
     * explicitly truncated (Phase 7.9). Counted as a low-cardinality metric
     * and emitted as an observable lifecycle event - truncation is never
     * silent.
     */
    public void toolResultTruncated(String requestId, String correlationId, Long conversationId, Long userId,
                                    String toolName, int originalLength, int maxLength) {
        safe(() -> {
            counter("agent.tool.truncations", "tool", safe(toolName)).increment();
            emitBusiness(AgentLifecycleEventType.TOOL_RESULT_TRUNCATED, requestId, correlationId,
                    conversationId, userId, map("tool", toolName,
                            "originalLength", originalLength, "maxLength", maxLength), null);
        });
    }

    /**
     * Record that asynchronous turn persistence failed (Phase 7.12). The user
     * response has already been delivered, so this is an observable,
     * non-blocking data-loss signal - never a corrupt or falsely-successful
     * persistence.
     */
    public void persistenceFailed(String requestId, String correlationId, Long conversationId, Long userId) {
        safe(() -> {
            counter("agent.persistence.errors").increment();
            emitBusiness(AgentLifecycleEventType.PERSISTENCE_FAILED, requestId, correlationId,
                    conversationId, userId, map("errorCategory", AgentErrorCategory.PERSISTENCE_ERROR.name()), null);
        });
    }

    // ------------------------------------------------------------------
    // Audit
    // ------------------------------------------------------------------

    /**
     * Emit a canonical audit event. Business- and security-significant actions
     * (authorization decisions, proposals, guardrail rejections) are audited
     * here; routine telemetry is not.
     */
    public void audit(AuditEvent event) {
        safe(() -> {
            Map<String, Object> details = map(
                    "auditEventId", event.auditEventId(),
                    "eventType", event.eventType(),
                    "userId", event.userId(),
                    "conversationId", event.conversationId(),
                    "requestId", event.requestId(),
                    "correlationId", event.correlationId(),
                    "tool", event.tool(),
                    "result", event.result(),
                    "reason", event.reason(),
                    "timestamp", event.timestamp() == null ? null : event.timestamp().toString());
            if (isSecurityAudit(event.eventType())) {
                emitSecurity("AUDIT", event.requestId(), event.correlationId(), event.conversationId(),
                        event.userId(), details);
            } else {
                emitBusiness("AUDIT", event.requestId(), event.correlationId(), event.conversationId(),
                        event.userId(), details, null);
            }
        });
    }

    private static boolean isSecurityAudit(String eventType) {
        if (eventType == null) {
            return false;
        }
        String u = eventType.toUpperCase();
        return u.contains("DENIED") || u.contains("AUTHORIZATION") || u.contains("SECURITY")
                || u.contains("REJECT") || u.contains("GUARDRAIL");
    }

    // ------------------------------------------------------------------
    // Metric helpers
    // ------------------------------------------------------------------

    private Counter counter(String name, String... tags) {
        if (meterRegistry == null) {
            return null;
        }
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }

    private void recordTiming(String name, long durationMs, String... tags) {
        if (meterRegistry == null) {
            return;
        }
        Timer.builder(name).tags(tags).register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /** Expose the existing Phase 5 cache counters as gauges (reuse, never duplicate). */
    private void registerCacheGauges(@Nullable CacheService cacheService) {
        if (meterRegistry == null || cacheService == null) {
            return;
        }
        CacheMetrics metrics = cacheService.metrics();
        if (metrics == null) {
            return;
        }
        meterRegistry.gauge("agent.cache.hits", metrics, CacheMetrics::hits);
        meterRegistry.gauge("agent.cache.misses", metrics, CacheMetrics::misses);
        meterRegistry.gauge("agent.cache.errors", metrics, CacheMetrics::errors);
    }

    // ------------------------------------------------------------------
    // Emission helpers
    // ------------------------------------------------------------------

    private void emitBusiness(AgentLifecycleEventType type, String requestId, String correlationId,
                              Long conversationId, Long userId, Map<String, Object> extras, Long durationMs) {
        emit("business", type.name(), requestId, correlationId, conversationId, userId, extras, durationMs);
    }

    private void emitBusiness(String eventType, String requestId, String correlationId,
                              Long conversationId, Long userId, Map<String, Object> extras, Long durationMs) {
        emit("business", eventType, requestId, correlationId, conversationId, userId, extras, durationMs);
    }

    private void emitSecurity(AgentLifecycleEventType type, String requestId, String correlationId,
                              Long conversationId, Long userId, Map<String, Object> extras) {
        emit("security", type.name(), requestId, correlationId, conversationId, userId, extras, null);
    }

    private void emitSecurity(String eventType, String requestId, String correlationId,
                              Long conversationId, Long userId, Map<String, Object> details) {
        emit("security", eventType, requestId, correlationId, conversationId, userId, details, null);
    }

    private void emit(String channel, String eventType, String requestId, String correlationId,
                      Long conversationId, Long userId, Map<String, Object> extras, Long durationMs) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventType", eventType);
        details.put("component", COMPONENT);
        details.put("requestId", requestId);
        details.put("correlationId", correlationId);
        details.put("conversationId", conversationId);
        details.put("userId", userId);
        details.putAll(extras);
        if (eventLogger != null) {
            if ("security".equals(channel)) {
                eventLogger.logSecurityEvent(null, null, details);
            } else {
                eventLogger.logBusinessEvent(null, null, details);
            }
        }
        if (durationMs != null && performanceLogger != null) {
            performanceLogger.log("BUSINESS", "agent." + eventType.toLowerCase().replace('_', '.'), durationMs, details);
        }
    }

    private static Map<String, Object> map(Object... keyValues) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            m.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return m;
    }

    private String label(AgentErrorCategory category) {
        return category == null ? "UNKNOWN" : category.name();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private Object tokens(Integer tokens) {
        // Token usage is recorded only when reliably available; -1 / null = unavailable.
        return (tokens == null || tokens < 0) ? "unavailable" : tokens;
    }

    private Integer total(Integer prompt, Integer completion) {
        boolean promptOk = prompt != null && prompt >= 0;
        boolean completionOk = completion != null && completion >= 0;
        if (promptOk && completionOk) {
            return prompt + completion;
        }
        return -1;
    }

    /** Fail-safe: never let telemetry break the business operation. */
    private static void safe(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // Observability must never become a dependency of the business flow.
        }
    }
}