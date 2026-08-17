package com.claimassist.platform.agent_service.observability;

/**
 * Canonical, meaningful stages in the agent request lifecycle (Phase 6.2).
 * <p>
 * These are the only lifecycle events emitted - deliberately coarse so that a
 * single request produces a small, readable event chain that answers the
 * operational questions ("did it start, did the LLM run, which tools ran, how
 * did it end") without burying engineers in per-token or per-timestep noise.
 *
 * <pre>
 * REQUEST_STARTED → CONTEXT_BUILT → LLM_STARTED → TOOL_REQUESTED → TOOL_STARTED
 *   → TOOL_COMPLETED | TOOL_FAILED → LLM_COMPLETED | LLM_FAILED
 *   → RESPONSE_COMPLETED | RESPONSE_FAILED
 * </pre>
 *
 * SSE lifecycle (stream) is tracked separately: {@code STREAM_STARTED /
 * STREAM_COMPLETED / STREAM_FAILED / STREAM_CANCELLED}.
 */
public enum AgentLifecycleEventType {

    // Request / context
    REQUEST_STARTED,
    CONTEXT_BUILT,
    RESPONSE_COMPLETED,
    RESPONSE_FAILED,

    // LLM
    LLM_STARTED,
    LLM_COMPLETED,
    LLM_FAILED,

    // Tool
    TOOL_REQUESTED,
    TOOL_STARTED,
    TOOL_COMPLETED,
    TOOL_FAILED,
    TOOL_RESULT_TRUNCATED,

    // SSE stream
    STREAM_STARTED,
    STREAM_COMPLETED,
    STREAM_FAILED,
    STREAM_CANCELLED,

    // Security / guardrails
    SECURITY_DENIED,
    PROMPT_GUARDRAIL_REJECTED,

    // Reliability (Phase 7)
    PERSISTENCE_FAILED
}