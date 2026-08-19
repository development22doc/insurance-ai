package com.claimassist.platform.agent_service.security;

import com.claimassist.platform.agent_service.config.AgentAiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OutputGuardrailsTest {

    private OutputGuardrails enabled() {
        AgentAiProperties props = new AgentAiProperties();
        props.setEnableOutputGuardrail(true);
        return new OutputGuardrails(props);
    }

    @Test
    void returnsEmptyWhenNoHardOutcomeAsserted() {
        assertThat(enabled().advisory("Your claim is being processed.", List.of("APPROVED")))
                .isEmpty();
    }

    @Test
    void returnsEmptyWhenHardOutcomeIsGrounded() {
        assertThat(enabled().advisory("your claim has been approved", List.of("APPROVED")))
                .isEmpty();
    }

    @Test
    void returnsAdvisoryWhenHardOutcomeNotGrounded() {
        Optional<String> advisory = enabled().advisory("your claim has been approved", List.of("PENDING"));
        assertThat(advisory).isPresent();
        assertThat(advisory.get()).contains("verify with the claim system");
    }

    @Test
    void returnsEmptyWhenNullOrBlankText() {
        assertThat(enabled().advisory(null, List.of("APPROVED"))).isEmpty();
        assertThat(enabled().advisory("   ", List.of("APPROVED"))).isEmpty();
    }

    @Test
    void toleratesNullGroundedStatuses() {
        assertThat(enabled().advisory("your policy is now denied", null)).isPresent();
    }

    @Test
    void normalizesCaseAndWhitespaceOfGroundedStatus() {
        assertThat(enabled().advisory("your claim has been APPROVED", List.of("  approved  ")))
                .isEmpty();
    }

    @Test
    void disabledGuardrailReturnsEmpty() {
        AgentAiProperties props = new AgentAiProperties();
        props.setEnableOutputGuardrail(false);
        OutputGuardrails out = new OutputGuardrails(props);
        assertThat(out.advisory("your claim has been approved", List.of("PENDING"))).isEmpty();
    }
}
