package com.claimassist.platform.agent_service.ai.tool;

/**
 * Wraps an unexpected failure while executing a tool (e.g. an unexpected
 * runtime exception from a backend gateway). The cause is preserved for logs
 * but must never be exposed to the end user.
 */
public class ToolExecutionException extends RuntimeException {

    public ToolExecutionException(String toolName, Throwable cause) {
        super("Tool '" + toolName + "' failed to execute.", cause);
    }
}