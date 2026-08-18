package com.claimassist.platform.agent_service.ai.tool;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class QwenToolCallTest {

    @Test
    void constructsValidToolCall() {
        Map<String, Object> args = Map.of("claimId", 1L);
        QwenToolCall call = new QwenToolCall("lookup_policy", args);
        assertThat(call.name()).isEqualTo("lookup_policy");
        assertThat(call.arguments()).isEqualTo(args);
    }

    @Test
    void rejectsBlankName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new QwenToolCall("  ", Map.of()))
                .withMessage("tool name must not be blank");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new QwenToolCall(null, Map.of()))
                .withMessage("tool name must not be blank");
    }

    @Test
    void rejectsNullArguments() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new QwenToolCall("lookup_policy", null))
                .withMessage("tool arguments must not be null");
    }
}