package com.claimassist.platform.agent_service.memory;

import com.claimassist.platform.agent_service.config.AgentAiProperties;
import com.claimassist.platform.common_lib.enums.MessageRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationContextBuilderTest {

    private final AgentAiProperties props = new AgentAiProperties();
    private final ConversationContextBuilder builder = new ConversationContextBuilder(props);

    @Test
    void emptyHistoryKeepsSystemPromptUnchanged() {
        ConversationContextBuilder.Context ctx =
                builder.build("You are ClaimAssist.", "hello", List.of());
        assertThat(ctx.systemPrompt()).isEqualTo("You are ClaimAssist.");
        assertThat(ctx.userMessage()).isEqualTo("hello");
    }

    @Test
    void nullHistoryIsTreatedAsEmpty() {
        assertThat(builder.renderHistory(null)).isEmpty();
    }

    @Test
    void rendersHistoryChronologicallyWithRoleLabels() {
        List<ConversationMessage> history = List.of(
                new ConversationMessage(MessageRole.USER, "My claim is CLM-123."),
                new ConversationMessage(MessageRole.ASSISTANT, "Got it."));
        String transcript = builder.renderHistory(history);
        assertThat(transcript)
                .contains("[USER] My claim is CLM-123.")
                .contains("[ASSISTANT] Got it.");
        // chronological: user line before assistant line
        assertThat(transcript.indexOf("[USER] My claim"))
                .isLessThan(transcript.indexOf("[ASSISTANT] Got it."));
    }

    @Test
    void buildAppendsTranscriptToSystemPrompt() {
        List<ConversationMessage> history = List.of(
                new ConversationMessage(MessageRole.USER, "My claim is CLM-123."));
        ConversationContextBuilder.Context ctx =
                builder.build("You are ClaimAssist.", "What is its status?", history);
        assertThat(ctx.systemPrompt()).contains("You are ClaimAssist.")
                .contains("[USER] My claim is CLM-123.")
                .contains("End of previous conversation");
        assertThat(ctx.userMessage()).isEqualTo("What is its status?");
    }

    @Test
    void dropsOldestMessagesWhenCharacterBudgetExceeded() {
        AgentAiProperties small = new AgentAiProperties();
        small.setMaxContextChars(30);
        ConversationContextBuilder b = new ConversationContextBuilder(small);
        List<ConversationMessage> history = List.of(
                new ConversationMessage(MessageRole.USER, "A".repeat(40)),
                new ConversationMessage(MessageRole.USER, "B".repeat(5)),
                new ConversationMessage(MessageRole.ASSISTANT, "C".repeat(5)));
        String transcript = b.renderHistory(history);
        // The most recent entries must be kept; the oldest oversized entry dropped.
        assertThat(transcript).contains("[USER] BBBBB").contains("[ASSISTANT] CCCCC");
        assertThat(transcript).doesNotContain("AAAA");
    }

    @Test
    void alwaysKeepsAtLeastMostRecentMessageEvenIfOverBudget() {
        AgentAiProperties tiny = new AgentAiProperties();
        tiny.setMaxContextChars(5);
        ConversationContextBuilder b = new ConversationContextBuilder(tiny);
        List<ConversationMessage> history = List.of(
                new ConversationMessage(MessageRole.USER, "old"),
                new ConversationMessage(MessageRole.ASSISTANT, "very long recent message"));
        String transcript = b.renderHistory(history);
        assertThat(transcript).contains("very long recent message");
    }
}