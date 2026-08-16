package com.claimassist.platform.agent_service.ai.tool;

/**
 * Thrown when a single tool call does not complete within the configured
 * per-tool timeout. Surfaced to the client as a controlled error, never as a
 * raw stack trace.
 */
public class ToolExecutionTimeoutException extends RuntimeException {

    public ToolExecutionTimeoutException(String toolName, long timeoutMs) {
        super("Tool '" + toolName + "' timed out after " + timeoutMs + "ms.");
    }
}