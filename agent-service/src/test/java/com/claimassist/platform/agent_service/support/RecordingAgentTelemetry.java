package com.claimassist.platform.agent_service.support;

import com.claimassist.platform.agent_service.observability.AgentErrorCategory;
import com.claimassist.platform.agent_service.observability.AgentTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double that records every telemetry lifecycle call in thread-safe order
 * so tests can assert SCENARIO event sequences (REQUEST_STARTED → ... →
 * RESPONSE_COMPLETED) deterministically. All recording calls delegate to a real
 * {@link AgentTelemetry} bound to a {@link SimpleMeterRegistry}.
 */
public class RecordingAgentTelemetry extends AgentTelemetry {

    /** Recorded lifecycle events: {@code eventType} (with correlationId when known). */
    public record Event(String eventType, String requestId) {}

    private final List<Event> events = new CopyOnWriteArrayList<>();

    public RecordingAgentTelemetry() {
        super(null, null, new SimpleMeterRegistry(), null);
    }

    public List<Event> events() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }

    public List<String> eventTypes() {
        return events.stream().map(Event::eventType).toList();
    }

    private void record(String type, String requestId) {
        events.add(new Event(type, requestId));
    }

    @Override
    public void requestStarted(String requestId, String correlationId, Long conversationId,
                               Long userId, String model, String provider) {
        record("REQUEST_STARTED", requestId);
        super.requestStarted(requestId, correlationId, conversationId, userId, model, provider);
    }

    @Override
    public void contextBuilt(String requestId, String correlationId, Long conversationId,
                             Long userId, long durationMs, int contextMessages) {
        record("CONTEXT_BUILT", requestId);
        super.contextBuilt(requestId, correlationId, conversationId, userId, durationMs, contextMessages);
    }

    @Override
    public void llmStarted(String requestId, String correlationId, Long conversationId,
                           Long userId, String model, String provider) {
        record("LLM_STARTED", requestId);
        super.llmStarted(requestId, correlationId, conversationId, userId, model, provider);
    }

    @Override
    public void llmCompleted(String requestId, String correlationId, Long conversationId, Long userId,
                             long durationMs, String finishReason, int toolCallCount,
                             Integer promptTokens, Integer completionTokens) {
        record("LLM_COMPLETED", requestId);
        super.llmCompleted(requestId, correlationId, conversationId, userId, durationMs,
                finishReason, toolCallCount, promptTokens, completionTokens);
    }

    @Override
    public void llmFailed(String requestId, String correlationId, Long conversationId, Long userId,
                          long durationMs, AgentErrorCategory errorCategory) {
        record("LLM_FAILED", requestId);
        super.llmFailed(requestId, correlationId, conversationId, userId, durationMs, errorCategory);
    }

    @Override
    public void responseCompleted(String requestId, String correlationId, Long conversationId,
                                  Long userId, long durationMs) {
        record("RESPONSE_COMPLETED", requestId);
        super.responseCompleted(requestId, correlationId, conversationId, userId, durationMs);
    }

    @Override
    public void responseFailed(String requestId, String correlationId, Long conversationId,
                               Long userId, long durationMs, AgentErrorCategory errorCategory) {
        record("RESPONSE_FAILED", requestId);
        super.responseFailed(requestId, correlationId, conversationId, userId, durationMs, errorCategory);
    }

    @Override
    public void streamStarted(String requestId, String correlationId, Long conversationId, Long userId) {
        record("STREAM_STARTED", requestId);
        super.streamStarted(requestId, correlationId, conversationId, userId);
    }

    @Override
    public void streamCompleted(String requestId, String correlationId, Long conversationId, Long userId) {
        record("STREAM_COMPLETED", requestId);
        super.streamCompleted(requestId, correlationId, conversationId, userId);
    }

    @Override
    public void streamFailed(String requestId, String correlationId, Long conversationId, Long userId,
                             AgentErrorCategory errorCategory) {
        record("STREAM_FAILED", requestId);
        super.streamFailed(requestId, correlationId, conversationId, userId, errorCategory);
    }

    @Override
    public void streamCancelled(String requestId, String correlationId, Long conversationId, Long userId) {
        record("STREAM_CANCELLED", requestId);
        super.streamCancelled(requestId, correlationId, conversationId, userId);
    }

    @Override
    public void guardrailRejected(String requestId, String correlationId, Long conversationId, Long userId,
                                  String code, String message) {
        record("PROMPT_GUARDRAIL_REJECTED", requestId);
        super.guardrailRejected(requestId, correlationId, conversationId, userId, code, message);
    }

    @Override
    public void securityDenied(String requestId, String correlationId, Long conversationId, Long userId,
                               String tool, String reason) {
        record("SECURITY_DENIED", requestId);
        super.securityDenied(requestId, correlationId, conversationId, userId, tool, reason);
    }

    @Override
    public void toolRequested(String requestId, String correlationId, Long conversationId, Long userId,
                              String toolName, String claimIdHash) {
        record("TOOL_REQUESTED", requestId);
        super.toolRequested(requestId, correlationId, conversationId, userId, toolName, claimIdHash);
    }

    @Override
    public void toolStarted(String requestId, String correlationId, Long conversationId, Long userId,
                            String toolName, String claimIdHash) {
        record("TOOL_STARTED", requestId);
        super.toolStarted(requestId, correlationId, conversationId, userId, toolName, claimIdHash);
    }

    @Override
    public void toolCompleted(String requestId, String correlationId, Long conversationId, Long userId,
                              String toolName, String claimIdHash, long durationMs, String result) {
        record("TOOL_COMPLETED", requestId);
        super.toolCompleted(requestId, correlationId, conversationId, userId, toolName, claimIdHash,
                durationMs, result);
    }

    @Override
    public void toolFailed(String requestId, String correlationId, Long conversationId, Long userId,
                           String toolName, String claimIdHash, long durationMs, String result,
                           AgentErrorCategory errorCategory) {
        record("TOOL_FAILED", requestId);
        super.toolFailed(requestId, correlationId, conversationId, userId, toolName, claimIdHash,
                durationMs, result, errorCategory);
    }

    @Override
    public void toolResultTruncated(String requestId, String correlationId, Long conversationId, Long userId,
                                    String toolName, int originalLength, int maxLength) {
        record("TOOL_RESULT_TRUNCATED", requestId);
        super.toolResultTruncated(requestId, correlationId, conversationId, userId, toolName,
                originalLength, maxLength);
    }

    @Override
    public void persistenceFailed(String requestId, String correlationId, Long conversationId, Long userId) {
        record("PERSISTENCE_FAILED", requestId);
        super.persistenceFailed(requestId, correlationId, conversationId, userId);
    }
}