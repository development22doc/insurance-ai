package com.claimassist.platform.claims_service.consumer;

import com.claimassist.platform.claims_service.entity.Claim;
import com.claimassist.platform.claims_service.entity.ProcessedEvent;
import com.claimassist.platform.claims_service.repository.OutboxEventRepository;
import com.claimassist.platform.claims_service.repository.ProcessedEventRepository;
import com.claimassist.platform.claims_service.security.SecurityExpressions;
import com.claimassist.platform.claims_service.service.command.ClaimCommandService;
import com.claimassist.platform.claims_service.service.command.ClaimCommands.UpdateClaimStatusCommand;
import com.claimassist.platform.common_lib.enums.ClaimPermission;
import com.claimassist.platform.common_lib.error.ClaimStateTransitionException;
import com.claimassist.platform.common_lib.event.ClaimUpdateRequestEvent;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimUpdateConsumerTest {

    @Mock
    private ClaimCommandService commandService;

    @Mock
    private SecurityExpressions securityExpressions;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private EventLogger eventLogger;

    @Mock
    private Acknowledgment ack;

    private ObjectMapper objectMapper = new ObjectMapper();

    private ClaimUpdateConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ClaimUpdateConsumer(commandService, securityExpressions,
                processedEventRepository, outboxEventRepository, objectMapper, eventLogger);
    }

    private ClaimUpdateRequestEvent request() {
        return new ClaimUpdateRequestEvent(100L, "saga-1", "APPROVED", "note", 5L);
    }

    private String json(Object request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void duplicateSagaRequeuesPreviousAckWithoutReapplyingChange() throws Exception {
        when(processedEventRepository.existsById("saga-1")).thenReturn(true);

        consumer.consumeClaimUpdateRequest(json(request()), null, null, null, ack);

        verify(commandService, never()).applyStatusChange(any(UpdateClaimStatusCommand.class));
        verify(ack).acknowledge();
    }

    @Test
    void unauthorizedUserRejectsAndQueuesFailureResponse() throws Exception {
        when(processedEventRepository.existsById("saga-1")).thenReturn(false);
        when(securityExpressions.hasPermissionForUser(100L, 5L, ClaimPermission.UPDATE_STATUS)).thenReturn(false);

        consumer.consumeClaimUpdateRequest(json(request()), null, null, null, ack);

        verify(commandService, never()).applyStatusChange(any(UpdateClaimStatusCommand.class));
        verify(processedEventRepository).save(any(ProcessedEvent.class));
        verify(outboxEventRepository).save(org.mockito.ArgumentMatchers.argThat(
                e -> e.getEventType().equals("ClaimUpdateResponseEvent")
                        && e.getAggregateId().equals("saga-1")));
    }

    @Test
    void invalidStateTransitionRejectsAndQueuesFailureResponse() throws Exception {
        when(processedEventRepository.existsById("saga-1")).thenReturn(false);
        when(securityExpressions.hasPermissionForUser(100L, 5L, ClaimPermission.UPDATE_STATUS)).thenReturn(true);
        when(commandService.applyStatusChange(any(UpdateClaimStatusCommand.class)))
                .thenThrow(new ClaimStateTransitionException("SUBMITTED", "APPROVED"));

        consumer.consumeClaimUpdateRequest(json(request()), null, null, null, ack);

        verify(processedEventRepository).save(any(ProcessedEvent.class));
        verify(outboxEventRepository).save(org.mockito.ArgumentMatchers.argThat(
                e -> e.getAggregateId().equals("saga-1")
                        && e.getTopic().equals("claim-update-response-event")));
    }

    @Test
    void authorizedSuccessAppliesChangeAndQueuesSuccessResponse() throws Exception {
        when(processedEventRepository.existsById("saga-1")).thenReturn(false);
        when(securityExpressions.hasPermissionForUser(100L, 5L, ClaimPermission.UPDATE_STATUS)).thenReturn(true);
        Claim claim = new Claim();
        when(commandService.applyStatusChange(any(UpdateClaimStatusCommand.class))).thenReturn(claim);

        consumer.consumeClaimUpdateRequest(json(request()), "corr-1", "trace-1", "span-1", ack);

        verify(commandService).applyStatusChange(any(UpdateClaimStatusCommand.class));
        verify(processedEventRepository).save(any(ProcessedEvent.class));
        verify(outboxEventRepository).save(org.mockito.ArgumentMatchers.argThat(
                e -> e.getEventType().equals("ClaimUpdateResponseEvent")
                        && e.getPartitionKey().equals("claim-100")));
        verify(ack).acknowledge();
    }

    @Test
    void responseNotDuplicatedWhenAlreadyQueued() throws Exception {
        when(processedEventRepository.existsById("saga-1")).thenReturn(false);
        when(securityExpressions.hasPermissionForUser(100L, 5L, ClaimPermission.UPDATE_STATUS)).thenReturn(true);
        when(commandService.applyStatusChange(any(UpdateClaimStatusCommand.class))).thenReturn(new Claim());
        when(outboxEventRepository.findFirstByAggregateIdAndEventType("saga-1", "ClaimUpdateResponseEvent"))
                .thenReturn(Optional.of(new com.claimassist.platform.claims_service.entity.OutboxEvent()));

        consumer.consumeClaimUpdateRequest(json(request()), null, null, null, ack);

        verify(outboxEventRepository, never()).save(any(com.claimassist.platform.claims_service.entity.OutboxEvent.class));
    }

    @Test
    void parsesCorrelationIdFromHeaderOverSagaId() throws Exception {
        when(processedEventRepository.existsById("saga-1")).thenReturn(false);
        when(securityExpressions.hasPermissionForUser(100L, 5L, ClaimPermission.UPDATE_STATUS)).thenReturn(true);
        when(commandService.applyStatusChange(any(UpdateClaimStatusCommand.class))).thenReturn(new Claim());

        consumer.consumeClaimUpdateRequest(json(request()), "hdr-corr", null, null, ack);

        assertThat(org.slf4j.MDC.get(com.claimassist.platform.common_lib.observability.LoggingConstants.MDC_CORRELATION_ID))
                .isNull();
        org.slf4j.MDC.clear();
    }
}