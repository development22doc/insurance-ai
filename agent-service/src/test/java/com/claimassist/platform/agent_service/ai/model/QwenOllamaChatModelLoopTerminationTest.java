package com.claimassist.platform.agent_service.ai.model;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the loop-termination helper of {@link QwenOllamaChatModel}: when a
 * pathological tool loop is aborted, the response returned to the client is a
 * plain natural-language message that contains no raw tool-call JSON.
 */
class QwenOllamaChatModelLoopTerminationTest {

    @Test
    void controlledTerminationResponseIsPlainTextWithoutToolJson() {
        // The loop-termination response must be plain prose and must never carry
        // the raw tool-call object the model kept repeating.
        ChatResponse response = QwenOllamaChatModel.controlledTerminationChatResponse();

        String text = response.getResult().getOutput().getText();
        assertThat(text).isNotBlank();
        assertThat(text).doesNotContain("\"name\"", "\"arguments\"", "propose_claim_update");
        assertThat(text).isEqualTo(QwenOllamaChatModel.CONTROLLED_TERMINATION_MESSAGE);
    }
}