package com.claimassist.platform.agent_service.ai.tool;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Determines whether tool execution should run for a given model response.
 * <p>
 * This mirrors {@code DefaultToolExecutionEligibilityPredicate} for native tool
 * calls (tool execution enabled <em>and</em> the response carries native tool
 * calls), and additionally triggers execution when the assistant content is a
 * complete, valid, registered Qwen text tool-call object. The decision is
 * purely structural - the {@link QwenToolCallParser} is the input boundary and
 * the {@link QwenToolCallingManager} remains authoritative for execution.
 */
public class QwenToolExecutionEligibilityPredicate implements ToolExecutionEligibilityPredicate {

    private final QwenToolCallParser parser;

    public QwenToolExecutionEligibilityPredicate(QwenToolCallParser parser) {
        this.parser = parser;
    }

    @Override
    public boolean test(ChatOptions promptOptions, ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResults() == null) {
            return false;
        }
        if (!ToolCallingChatOptions.isInternalToolExecutionEnabled(promptOptions)) {
            return false;
        }
        if (chatResponse.hasToolCalls()) {
            return true;
        }
        if (!(promptOptions instanceof ToolCallingChatOptions toolCallingChatOptions)) {
            return false;
        }
        Set<String> registeredNames = toolCallingChatOptions.getToolCallbacks().stream()
                .map(cb -> cb.getToolDefinition().name())
                .collect(Collectors.toSet());
        String text = chatResponse.getResults().stream()
                .map(g -> g.getOutput().getText())
                .reduce("", (a, b) -> a == null ? b : a + b);
        return text != null && parser.isCompleteToolCall(text, registeredNames);
    }
}