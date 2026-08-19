package com.claimassist.platform.agent_service.ai.tool;

import com.claimassist.platform.agent_service.ai.tool.QwenToolCallParser;
import com.claimassist.platform.agent_service.ai.tool.QwenToolExecutionEligibilityPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Structural eligibility predicate: tool execution runs only for responses that
 * either carry native Spring AI tool calls or a complete, valid, registered Qwen
 * text tool-call object, when internal tool execution is enabled.
 */
class QwenToolExecutionEligibilityPredicateTest {

    private QwenToolCallParser parser;
    private QwenToolExecutionEligibilityPredicate predicate;
    private ToolCallback getClaimStatus;
    private ToolCallback getClaimDocuments;

    @BeforeEach
    void setUp() {
        parser = new QwenToolCallParser();
        predicate = new QwenToolExecutionEligibilityPredicate(parser);
        MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(new ProbeTools())
                .build();
        List<ToolCallback> callbacks = List.of(provider.getToolCallbacks());
        getClaimStatus = callbacks.get(0);
        getClaimDocuments = callbacks.get(1);
    }

    private ToolCallingChatOptions options(boolean internalExecutionEnabled) {
        return DefaultToolCallingChatOptions.builder()
                .toolCallbacks(List.of(getClaimStatus, getClaimDocuments))
                .internalToolExecutionEnabled(internalExecutionEnabled)
                .build();
    }

    private static ChatResponse responseWithText(String assistantText) {
        AssistantMessage am = AssistantMessage.builder()
                .content(assistantText)
                .properties(Map.of())
                .build();
        return new ChatResponse(List.of(new Generation(am)));
    }

    @Test
    void nullResponseIsIneligible() {
        assertThat(predicate.test(options(true), null)).isFalse();
    }

    @Test
    void responseWithoutResultsIsIneligible() {
        ChatResponse response = mock(ChatResponse.class);
        when(response.getResults()).thenReturn(null);
        when(response.hasToolCalls()).thenReturn(false);

        assertThat(predicate.test(options(true), response)).isFalse();
    }

    @Test
    void internalToolExecutionDisabledIsIneligible() {
        assertThat(predicate.test(options(false), responseWithText(
                "{\"name\":\"get_claim_status\",\"arguments\":{}}"))).isFalse();
    }

    @Test
    void nativeSpringAiToolCallIsEligible() {
        ChatResponse response = mock(ChatResponse.class);
        AssistantMessage am = AssistantMessage.builder()
                .content("")
                .properties(Map.of())
                .toolCalls(List.of(new AssistantMessage.ToolCall("id1", "function", "get_claim_status", "{}")))
                .build();
        when(response.getResults()).thenReturn(List.of(new Generation(am)));
        when(response.hasToolCalls()).thenReturn(true);

        assertThat(predicate.test(options(true), response)).isTrue();
    }

    @Test
    void nonToolCallingOptionsAreIneligible() {
        ChatOptions plainOptions = mock(ChatOptions.class);

        assertThat(predicate.test(plainOptions, responseWithText("ordinary prose"))).isFalse();
    }

    @Test
    void completeRegisteredQwenToolCallIsEligible() {
        assertThat(predicate.test(options(true), responseWithText(
                "{\"name\":\"get_claim_status\",\"arguments\":{\"claimId\":\"c1\"}}"))).isTrue();
    }

    @Test
    void unknownToolNameIsIneligible() {
        assertThat(predicate.test(options(true), responseWithText(
                "{\"name\":\"rm_rf\",\"arguments\":{}}"))).isFalse();
    }

    @Test
    void incompleteToolJsonIsIneligible() {
        assertThat(predicate.test(options(true), responseWithText(
                "{\"name\":\"get_claim_st"))).isFalse();
    }

    @Test
    void ordinaryProseIsIneligibleAndShouldNotRecurseIntoParser() {
        assertThat(predicate.test(options(true), responseWithText("Claim status is UNDER_REVIEW."))).isFalse();
    }

    /** Real tool object registered through the same MethodToolCallbackProvider path used in production. */
    static class ProbeTools {
        @Tool(name = "get_claim_status", description = "Get claim status")
        public String getClaimStatus(@ToolParam(description = "claim id") String claimId) {
            return "{\"status\":\"UNDER_REVIEW\"}";
        }

        @Tool(name = "get_claim_documents", description = "Get documents")
        public String getClaimDocuments() {
            return "{\"docs\":[]}";
        }
    }
}