package com.claimassist.platform.claims_service.saga;

import com.claimassist.platform.claims_service.dto.claim.ClaimResponse;
import com.claimassist.platform.claims_service.entity.Claim;
import com.claimassist.platform.claims_service.entity.SagaProcessedMessage;
import com.claimassist.platform.claims_service.repository.SagaProcessedMessageRepository;
import com.claimassist.platform.claims_service.service.command.ClaimCommandService;
import com.claimassist.platform.claims_service.service.command.ClaimCommands.SubmitClaimCommand;
import com.claimassist.platform.claims_service.service.command.ClaimCommands.UpdateClaimStatusCommand;
import com.claimassist.platform.common_lib.enums.SagaActionType;
import com.claimassist.platform.common_lib.enums.SagaStepType;
import com.claimassist.platform.common_lib.event.ClaimSagaStepCommandEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimSagaStepProcessorServiceTest {

    @Mock
    private ClaimCommandService commandService;

    @Mock
    private SagaOutboxPublisher outboxPublisher;

    @Mock
    private SagaProcessedMessageRepository processedMessageRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ClaimSagaStepProcessorService processor;

    private final AtomicInteger capturedResults = new AtomicInteger();

    @BeforeEach
    void setUp() {
        processor = new ClaimSagaStepProcessorService(commandService, outboxPublisher,
                processedMessageRepository, objectMapper);
        lenient().when(outboxPublisher.enqueueIfAbsent(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
    }

    private ClaimSagaStepCommandEvent command(SagaStepType step, String messageId) {
        return ClaimSagaStepCommandEvent.builder()
                .messageId(messageId)
                .sagaId("s1")
                .action(SagaActionType.CREATE_CLAIM)
                .step(step)
                .policyId(5L)
                .incidentType("FIRE")
                .incidentDate("2026-01-01T00:00:00Z")
                .estimatedAmountCents(1000L)
                .actorUserId(7L)
                .claimId(100L)
                .attempt(1)
                .build();
    }

    @Test
    void duplicateMessageIdIsSkipped() {
        when(processedMessageRepository.existsById("dup")).thenReturn(true);

        processor.processStep(command(SagaStepType.CREATE_CLAIM, "dup"));

        verify(commandService, never()).submitClaim(any(SubmitClaimCommand.class));
        verify(outboxPublisher, never()).enqueueIfAbsent(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void createClaimStepInvokesCommandAndPublishesSuccessResult() {
        when(processedMessageRepository.existsById("m1")).thenReturn(false);
        ClaimResponse response = new ClaimResponse(42L, "CLM-X", "SUBMITTED", "FIRE");
        when(commandService.submitClaim(any(SubmitClaimCommand.class))).thenReturn(response);

        processor.processStep(command(SagaStepType.CREATE_CLAIM, "m1"));

        verify(commandService).submitClaim(any(SubmitClaimCommand.class));
        verify(processedMessageRepository).save(any(SagaProcessedMessage.class));
        verify(outboxPublisher).enqueueIfAbsent(eq("s1"), anyString(),
                eq(ClaimSagaOrchestratorService.STEP_RESULT_TOPIC), eq("claim-42"), anyString());
    }

    @Test
    void approveClaimStepInvokesStatusChangeAndPublishesResult() {
        when(processedMessageRepository.existsById("m2")).thenReturn(false);
        Claim claim = new Claim();
        claim.setId(100L);
        when(commandService.applyStatusChange(any(UpdateClaimStatusCommand.class))).thenReturn(claim);

        processor.processStep(command(SagaStepType.APPROVE_CLAIM, "m2"));

        verify(commandService).applyStatusChange(any(UpdateClaimStatusCommand.class));
        verify(outboxPublisher).enqueueIfAbsent(eq("s1"), anyString(),
                eq(ClaimSagaOrchestratorService.STEP_RESULT_TOPIC), eq("claim-100"), anyString());
    }

    @Test
    void failedStepPublishesFailureResult() {
        when(processedMessageRepository.existsById("m3")).thenReturn(false);
        when(commandService.submitClaim(any(SubmitClaimCommand.class)))
                .thenThrow(new IllegalStateException("policy invalid"));

        processor.processStep(command(SagaStepType.CREATE_CLAIM, "m3"));

        verify(outboxPublisher).enqueueIfAbsent(eq("s1"), anyString(),
                eq(ClaimSagaOrchestratorService.STEP_RESULT_TOPIC), anyString(), anyString());
    }

    @Test
    void sideEffectStepPublishesSuccessWithoutCommand() {
        when(processedMessageRepository.existsById("m4")).thenReturn(false);

        processor.processStep(command(SagaStepType.PAYMENT, "m4"));

        verify(commandService, never()).submitClaim(any(SubmitClaimCommand.class));
        verify(outboxPublisher).enqueueIfAbsent(eq("s1"), anyString(),
                eq(ClaimSagaOrchestratorService.STEP_RESULT_TOPIC), anyString(), anyString());
    }
}