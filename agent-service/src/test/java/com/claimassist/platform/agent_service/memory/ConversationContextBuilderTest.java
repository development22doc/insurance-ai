package com.claimassist.platform.agent_service.memory;

import com.claimassist.platform.agent_service.config.AgentAiProperties;
import com.claimassist.platform.common_lib.enums.MessageRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationContextBuilderTest {

    private ConversationContextBuilder builder(int maxChars) {
        AgentAiProperties props = new AgentAiProperties();
        props.setMaxContextChars(maxChars);
        return new ConversationContextBuilder(props);
    }

    private ConversationMessage message(MessageRole role, String content) {
        return new ConversationMessage(role, content);
    }

    @Test
    void buildWithoutHistoryUsesPlainSystemInstructions() {
        ConversationContextBuilder.Context ctx = builder(1000)
                .build("You are a claims assistant.", "hello", List.of());
        assertThat(ctx.systemPrompt()).isEqualTo("You are a claims assistant.");
        assertThat(ctx.userMessage()).isEqualTo("hello");
    }

    @Test
    void buildWithHistoryAppendsLabelledTranscript() {
        List<ConversationMessage> history = List.of(
                message(MessageRole.USER, "what is my status?"),
                message(MessageRole.ASSISTANT, "your claim is pending"));
        ConversationContextBuilder.Context ctx = builder(1000)
                .build("You are a claims assistant.", "thanks", history);
        assertThat(ctx.systemPrompt()).contains("## Previous conversation (context)");
        assertThat(ctx.systemPrompt()).contains("[USER] what is my status?");
        assertThat(ctx.systemPrompt()).contains("[ASSISTANT] your claim is pending");
        assertThat(ctx.systemPrompt()).contains("## End of previous conversation");
    }

    @Test
    void renderHistoryReturnsEmptyForNullHistory() {
        assertThat(builder(1000).renderHistory(null)).isEmpty();
    }

    @Test
    void renderHistoryDropsOldestEntriesThatExceedBudget() {
        List<ConversationMessage> history = List.of(
                message(MessageRole.USER, "very old message that is long"),
                message(MessageRole.ASSISTANT, "recent short reply"));
        String rendered = builder(50).renderHistory(history);
        assertThat(rendered).contains("recent short reply");
        assertThat(rendered).doesNotContain("very old message");
    }

    @Test
    void renderHistoryKeepsAtLeastNewestTurnDespiteBudget() {
        List<ConversationMessage> history = List.of(
                message(MessageRole.USER, "a".repeat(500)), message(MessageRole.ASSISTANT, "b".repeat(500)));
        String rendered = builder(50).renderHistory(history);
        assertThat(rendered).contains("b".repeat(500));
    }
}