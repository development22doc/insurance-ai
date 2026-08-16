package com.claimassist.platform.agent_service.ai.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * {@link ToolCallback} decorator that routes every invocation through a
 * {@link ToolExecutionGuard}, so a tool call is counted and time-bounded even
 * though Spring AI drives the actual invocation internally.
 */
public class GuardedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolExecutionGuard guard;

    public GuardedToolCallback(ToolCallback delegate, ToolExecutionGuard guard) {
        this.delegate = delegate;
        this.guard = guard;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String input) {
        return guard.execute(delegate.getToolDefinition().name(), () -> delegate.call(input));
    }

    @Override
    public String call(String input, ToolContext context) {
        return guard.execute(delegate.getToolDefinition().name(), () -> delegate.call(input, context));
    }
}