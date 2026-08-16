package com.claimassist.platform.agent_service.dto.agent;

/**
 * A single SSE payload emitted by {@code /agent/stream}. Existing consumers
 * that only read {@code text} remain compatible - the extra fields are additive.
 *
 * @param text      the assistant text (a streamed token chunk, the full message,
 *                  or a safe error message)
 * @param eventType message | done | error
 * @param requestId correlation id for this agent request
 * @param done      true for the terminal event of a successful response
 * @param errorCode a stable, user-safe error code, only when eventType == error
 */
public record StreamResponse(
        String text,
        String eventType,
        String requestId,
        boolean done,
        String errorCode
) {

    /** Backward-compatible constructor: a plain message event. */
    public StreamResponse(String text) {
        this(text, "message", null, false, null);
    }

    public static StreamResponse message(String text, String requestId) {
        return new StreamResponse(text, "message", requestId, false, null);
    }

    public static StreamResponse done(String requestId) {
        return new StreamResponse("", "done", requestId, true, null);
    }

    public static StreamResponse error(String requestId, String errorCode, String message) {
        return new StreamResponse(message, "error", requestId, true, errorCode);
    }
}