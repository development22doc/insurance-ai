package com.claimassist.platform.agent_service.security;

import com.claimassist.platform.agent_service.config.AgentAiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OutputGuardrailsTest {

    private final AgentAiProperties props = new AgentAiProperties();

    @Test
    void noAdvisoryWhenNoHardOutcomeAsserted() {
        assertThat(new OutputGuardrails(props).advisory("Your claim is under review right now.", List.of("UNDER_REVIEW")))
                .isEmpty();
        assertThat(new OutputGuardrails(props).advisory("Here are the documents we have.", List.of()))
                .isEmpty();
    }

    @Test
    void noAdvisoryWhenHardOutcomeIsGrounded() {
        Optional<String> a = new OutputGuardrails(props)
                .advisory("Your claim has been approved. The payout is on its way.", List.of("APPROVED"));
        assertThat(a).isEmpty();
    }

    @Test
    void advisoryWhenHardOutcomeNotSupportedByGroundedFacts() {
        Optional<String> a = new OutputGuardrails(props)
                .advisory("Your claim has been approved. Congratulations!", List.of("UNDER_REVIEW"));
        assertThat(a).isPresent();
    }

    @Test
    void advisoryWhenNoGroundedStatusAvailable() {
        Optional<String> a = new OutputGuardrails(props)
                .advisory("Your claim was denied.", List.of());
        assertThat(a).isPresent();
    }

    @Test
    void advisoryWhenGroundingIsUnavailable() {
        Optional<String> a = new OutputGuardrails(props)
                .advisory("Your claim was denied.", List.of("UNAVAILABLE"));
        assertThat(a).isPresent();
    }

    @Test
    void noAdvisoryOnUnrelatedHardWords() {
        // "approved" only matters in the claim/policy outcome context.
        assertThat(new OutputGuardrails(props).advisory("Your expense report was approved.", List.of("UNDER_REVIEW")))
                .isEmpty();
    }

    @Test
    void canBeDisabled() {
        props.setEnableOutputGuardrail(false);
        assertThat(new OutputGuardrails(props)
                .advisory("Your claim has been approved.", List.of("UNDER_REVIEW"))).isEmpty();
    }
}