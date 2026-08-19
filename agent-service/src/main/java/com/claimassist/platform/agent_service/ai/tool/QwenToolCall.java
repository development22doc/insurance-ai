package com.claimassist.platform.agent_service.ai.tool;

import java.util.Map;

/**
 * A validated tool call recovered from model output text (e.g. the structured
 * JSON tool call that qwen2.5-coder emits inside assistant content rather than
 * as a native {@code message.tool_calls}).
 * <p>
 * Instances are only produced by {@link QwenToolCallParser} after every input
 * boundary check has passed, so {@code name} is always a plain identifier that
 * must resolve against the registered tool set and {@code arguments} is always
 * a non-null JSON object. Model output is never trusted here; the parser and
 * the {@link QwenToolCallingManager} remain authoritative.
 */
public record QwenToolCall(String name, Map<String, Object> arguments) {

    public QwenToolCall {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool name must not be blank");
        }
        if (arguments == null) {
            throw new IllegalArgumentException("tool arguments must not be null");
        }
    }
}