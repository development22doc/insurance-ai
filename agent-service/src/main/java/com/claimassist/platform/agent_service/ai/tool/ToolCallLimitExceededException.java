package com.claimassist.platform.agent_service.ai.tool;

/**
 * Thrown when a single agent request exceeds the configured maximum number of
 * tool calls. Prevents the model from spinning in an endless tool loop.
 */
public class ToolCallLimitExceededException extends RuntimeException {

    public ToolCallLimitExceededException(int limit) {
        super("Maximum tool-call limit of " + limit + " reached for this request.");
    }
}