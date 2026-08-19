package com.claimassist.platform.agent_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable limits for the agent's LLM/tool execution loop.
 * <p>
 * These are the only values that control how far one agent request may go,
 * so that an unproductive or runaway model can never burn unbounded time or
 * spin in an infinite tool loop. All values are externalized via
 * {@code agent.ai.*} (env: {@code AGENT_AI_*}).
 */
@Data
@ConfigurationProperties(prefix = "agent.ai")
public class AgentAiProperties {

    /** Hard cap on how many tool calls one agent request may issue. */
    private int maxToolCalls = 10;

    /** Per-tool-call execution timeout in milliseconds. */
    private long toolTimeoutMs = 15_000;

    /** Overall budget for a single agent request (streaming) in milliseconds. */
    private long agentTimeoutMs = 60_000;

    /**
     * Maximum length, in characters, of a single user message accepted by the
     * input guardrail. Longer input is rejected before the LLM is invoked.
     * Rationale: no legitimate insurance question needs more than this, and it
     * caps the cost/risk surface of a single request.
     */
    private int maxMessageLength = 4000;

    /**
     * Maximum length, in characters, of a proposed claim-update note that the
     * tool layer will accept. A longer note is rejected as an invalid tool
     * argument so that an oversized value can never reach the permanent audit
     * trail.
     */
    private int maxNoteLength = 1000;

    /** Master switch for the input (prompt-injection/length) guardrail. */
    private boolean enableInputGuardrail = true;

    /** Master switch for the output (hallucination/hard-claim) guardrail. */
    private boolean enableOutputGuardrail = true;

    /**
     * Maximum number of prior USER/ASSISTANT messages included in the LLM
     * context for one request. Bounded history is what keeps the Ollama model
     * context window from growing without limit.
     */
    private int maxContextMessages = 10;

    /**
     * Maximum number of characters of prior conversation history included in
     * the LLM context. This is a clearly documented SAFE APPROXIMATION of a
     * token budget (the current architecture has no exact token counter):
     * English text averages well under one token per character, so capping
     * characters under-approximates the token count and stays inside the
     * window. Oldest history is dropped first.
     */
    private int maxContextChars = 6000;

    /**
     * Ollama HTTP connect timeout in milliseconds (Phase 7.1 / 7.2). Fails fast
     * when Ollama is unconnectable instead of hanging on a black-holed host.
     * The outer {@code agentTimeoutMs} remains the authoritative bound for a
     * full LLM stream (which is bounded by the reactor {@code timeout}).
     */
    private long ollamaConnectTimeoutMs = 5000;

    /**
     * Ollama HTTP read timeout in milliseconds for the blocking (non-streaming)
     * chat path. Streaming is bounded by {@code agentTimeoutMs} so a legitimate
     * long generation is never cut short by this value.
     */
    private long ollamaReadTimeoutMs = 60_000;

    /**
     * Maximum number of characters a tool result may contribute back to the
     * LLM context (Phase 7.9). A larger result is truncated EXPLICITLY (with a
     * visible marker) and recorded via telemetry - never silently, and never
     * allowed to explode the model's context window. Truncation preserves the
     * head of the payload, which contains the meaningful business data.
     */
    private int maxToolResultChars = 10_000;
}