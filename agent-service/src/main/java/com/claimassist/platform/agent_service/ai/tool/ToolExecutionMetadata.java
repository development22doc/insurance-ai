package com.claimassist.platform.agent_service.ai.tool;

/**
 * Lightweight per-execution metadata captured for every tool call in an agent
 * request. This is the minimum needed to identify and trace a tool execution;
 * it is NOT the full AI observability framework (a later phase).
 *
 * @param requestId     the agent request id.
 * @param correlationId the request correlation id.
 * @param toolName      the tool that executed.
 * @param status        SUCCESS, TIMEOUT, LIMIT_EXCEEDED, or FAILED.
 * @param durationMs    execution duration in milliseconds.
 */
public record ToolExecutionMetadata(
        String requestId,
        String correlationId,
        String toolName,
        String status,
        long durationMs
) {
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    public static final String STATUS_LIMIT_EXCEEDED = "LIMIT_EXCEEDED";
    public static final String STATUS_FAILED = "FAILED";
}