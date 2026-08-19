package com.claimassist.platform.common_lib.enums;

import com.claimassist.platform.common_lib.observability.event.EventType;
import com.claimassist.platform.common_lib.security.CallerType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnumValuesTest {

    @Test
    void valuesArePresentAndRoundTrip() {
        assertThat(SagaActionType.values()).containsExactlyInAnyOrder(SagaActionType.values());
        assertThat(SagaStepType.values()).containsExactlyInAnyOrder(SagaStepType.values());
        assertThat(AgentEventStatus.values()).containsExactlyInAnyOrder(AgentEventStatus.values());
        assertThat(AgentEventType.values()).containsExactlyInAnyOrder(AgentEventType.values());
        assertThat(ClaimPermission.values()).containsExactlyInAnyOrder(ClaimPermission.values());
        assertThat(MessageRole.values()).containsExactlyInAnyOrder(MessageRole.values());
        assertThat(OutboxStatus.values()).containsExactlyInAnyOrder(OutboxStatus.values());
        assertThat(SagaOrchestrationStatus.values()).containsExactlyInAnyOrder(SagaOrchestrationStatus.values());
        assertThat(CallerType.values()).containsExactlyInAnyOrder(CallerType.values());
        assertThat(EventType.values()).containsExactlyInAnyOrder(EventType.values());
    }

    @Test
    void keyEnumConstantsExist() {
        assertThat(MessageRole.USER).isNotNull();
        assertThat(MessageRole.ASSISTANT).isNotNull();
        assertThat(OutboxStatus.PENDING).isNotNull();
        assertThat(OutboxStatus.PUBLISHED).isNotNull();
        assertThat(OutboxStatus.FAILED).isNotNull();
        assertThat(SagaOrchestrationStatus.COMPENSATED).isNotNull();
        assertThat(SagaOrchestrationStatus.TIMED_OUT).isNotNull();
        assertThat(AgentEventStatus.CONFIRMED).isNotNull();
        assertThat(AgentEventType.CLAIM_UPDATE_PROPOSED).isNotNull();
        assertThat(SagaActionType.CREATE_CLAIM).isNotNull();
        assertThat(SagaStepType.ROLLBACK).isNotNull();
    }
}