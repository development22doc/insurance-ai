package com.claimassist.platform.agent_service.consumer;

import com.claimassist.platform.agent_service.entity.AgentEvent;
import com.claimassist.platform.agent_service.repository.AgentEventRepository;
import com.claimassist.platform.common_lib.enums.AgentEventStatus;
import com.claimassist.platform.common_lib.event.ClaimUpdateResponseEvent;
import com.claimassist.platform.common_lib.messaging.AckUtils;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSagaResponseHandlerTest {

    @Mock
    private AgentEventRepository repository;

    @Mock
    private EventLogger eventLogger;

    private AgentSagaResponseHandler handler() {
        return new AgentSagaResponseHandler(repository, new ObjectMapper(), eventLogger);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private String json(ClaimUpdateResponseEvent response) throws Exception {
        return new ObjectMapper().writeValueAsString(response);
    }

    @Test
    void confirmsPendingEventOnSuccess() throws Exception {
        AgentEvent event = AgentEvent.builder().id(1L).status(AgentEventStatus.PENDING)
                .sagaId("saga-1").content("proposal").build();
        when(repository.findBySagaId("saga-1")).thenReturn(Optional.of(event));
        ClaimUpdateResponseEvent response = ClaimUpdateResponseEvent.builder()
                .sagaId("saga-1").claimId(10L).success(true).build();

        handler().handleClaimUpdateResponse(json(response), "claim-update-response-event",
                0, 1L, "corr-1", null, null, "req-1", mockAck());

        assertThat(event.getStatus()).isEqualTo(AgentEventStatus.CONFIRMED);
        verify(eventLogger).logKafkaEvent(null, null, java.util.Map.ofEntries(
                java.util.Map.entry("event", "claim.update.response.received"),
                java.util.Map.entry("sagaId", "saga-1"),
                java.util.Map.entry("claimId", 10L),
                java.util.Map.entry("success", true)));
    }

    @Test
    void failsEventWhenResponseUnsuccessful() throws Exception {
        AgentEvent event = AgentEvent.builder().id(1L).status(AgentEventStatus.PENDING)
                .sagaId("saga-1").content("proposal").build();
        when(repository.findBySagaId("saga-1")).thenReturn(Optional.of(event));
        ClaimUpdateResponseEvent response = ClaimUpdateResponseEvent.builder()
                .sagaId("saga-1").claimId(10L).success(false).errorMessage("not allowed").build();

        handler().handleClaimUpdateResponse(json(response), "claim-update-response-event",
                0, 1L, null, null, null, "req-2", mockAck());
 
        assertThat(event.getStatus()).isEqualTo(AgentEventStatus.FAILED);
        assertThat(event.getContent()).contains("REJECTED: not allowed");
    }

    @Test
    void isIdempotentWhenEventAlreadyHandled() throws Exception {
        AgentEvent event = AgentEvent.builder().id(1L).status(AgentEventStatus.CONFIRMED)
                .sagaId("saga-1").content("proposal").build();
        when(repository.findBySagaId("saga-1")).thenReturn(Optional.of(event));
        ClaimUpdateResponseEvent response = ClaimUpdateResponseEvent.builder()
                .sagaId("saga-1").claimId(10L).success(true).build();

        handler().handleClaimUpdateResponse(json(response), "claim-update-response-event",
                0, 1L, null, null, null, "req-3", mockAck());
 
        assertThat(event.getStatus()).isEqualTo(AgentEventStatus.CONFIRMED);
        verify(repository, never()).save(any());
    }

    @Test
    void leavesTerminalStateUnchangedWhenAlreadyConfirmed() throws Exception {
        AgentEvent event = AgentEvent.builder().id(1L).status(AgentEventStatus.CONFIRMED)
                .sagaId("saga-1").content("proposal").build();
        when(repository.findBySagaId("saga-1")).thenReturn(Optional.of(event));
        ClaimUpdateResponseEvent response = ClaimUpdateResponseEvent.builder()
                .sagaId("saga-1").claimId(10L).success(false).errorMessage("late failure").build();

        handler().handleClaimUpdateResponse(json(response), "claim-update-response-event",
                0, 1L, null, null, null, "req-4", mockAck());
 
        assertThat(event.getStatus()).isEqualTo(AgentEventStatus.CONFIRMED);
        assertThat(event.getContent()).doesNotContain("REJECTED");
    }

    private Acknowledgment mockAck() {
        Acknowledgment ack = org.mockito.Mockito.mock(Acknowledgment.class);
        // AckUtils.acknowledgeAfterCommit: no transaction active → ack immediately.
        ack.acknowledge();
        return ack;
    }
}