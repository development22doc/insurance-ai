package com.claimassist.platform.claims_service.saga;

import com.claimassist.platform.claims_service.entity.ClaimSagaOrchestration;
import com.claimassist.platform.claims_service.entity.SagaProcessedMessage;
import com.claimassist.platform.claims_service.repository.ClaimSagaOrchestrationRepository;
import com.claimassist.platform.claims_service.repository.SagaProcessedMessageRepository;
import com.claimassist.platform.common_lib.enums.SagaActionType;
import com.claimassist.platform.common_lib.enums.SagaOrchestrationStatus;
import com.claimassist.platform.common_lib.enums.SagaStepType;
import com.claimassist.platform.common_lib.event.ClaimSagaOrchestrationRequestEvent;
import com.claimassist.platform.common_lib.event.ClaimSagaStepResultEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimSagaOrchestratorServiceTest {

    private final ClaimSagaOrchestrationRepository sagaRepository = mock(ClaimSagaOrchestrationRepository.class);
    private final SagaProcessedMessageRepository processedMessageRepository = mock(SagaProcessedMessageRepository.class);
    private final SagaOutboxPublisher sagaOutboxPublisher = mock(SagaOutboxPublisher.class);
    private final SagaMetricsService sagaMetricsService = mock(SagaMetricsService.class);
    private final SagaCompensationHandler compensationHandler = mock(SagaCompensationHandler.class);
    private final SagaIdempotencyManager idempotencyManager = mock(SagaIdempotencyManager.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ClaimSagaOrchestratorService service;

    @BeforeEach
    void setUp() {
        service = new ClaimSagaOrchestratorService(sagaRepository, processedMessageRepository,
                sagaOutboxPublisher, sagaMetricsService, compensationHandler, idempotencyManager, objectMapper);
        ReflectionTestUtils.setField(service, "timeoutSeconds", 180L);
        ReflectionTestUtils.setField(service, "recoveryRetryDelaySeconds", 30L);
        ReflectionTestUtils.setField(service, "maxRecoveryAttempts", 3);
    }

    private ClaimSagaOrchestration saga(String sagaId, SagaOrchestrationStatus status, SagaStepType step) {
        return ClaimSagaOrchestration.builder()
                .id(1L)
                .sagaId(sagaId)
                .action(SagaActionType.APPROVE_CLAIM)
                .status(status)
                .currentStep(step)
                .claimId(10L)
                .attempts(0)
                .updatedAt(Instant.now().minusSeconds(2))
                .build();
    }

    private ClaimSagaStepResultEvent result(String messageId, String sagaId, SagaStepType step,
                                            boolean success, int attempt) {
        return ClaimSagaStepResultEvent.builder()
                .messageId(messageId).sagaId(sagaId).action(SagaActionType.APPROVE_CLAIM)
                .step(step).success(success).claimId(10L)
                .errorMessage(success ? null : "step boom").attempt(attempt)
                .build();
    }

    @Test
    void startOrchestration_newSaga_dispatchesInitialStep() {
        ClaimSagaOrchestrationRequestEvent req = ClaimSagaOrchestrationRequestEvent.builder()
                .messageId("msg-1").action(SagaActionType.CREATE_CLAIM)
                .claimId(10L).policyId(20L).build();
        when(processedMessageRepository.existsById("msg-1")).thenReturn(false);
        when(sagaRepository.findBySagaId(anyString())).thenReturn(Optional.empty());
        when(sagaRepository.save(any(ClaimSagaOrchestration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.startOrchestration(req);

        verify(sagaRepository).save(any(ClaimSagaOrchestration.class));
        verify(processedMessageRepository).save(any(SagaProcessedMessage.class));
        verify(sagaOutboxPublisher).enqueueIfAbsent(anyString(),
                eq("ClaimSagaStepCommandEvent:CREATE_CLAIM:1"),
                eq(ClaimSagaOrchestratorService.STEP_COMMAND_TOPIC), anyString(), anyString());
    }

    @Test
    void startOrchestration_duplicateMessage_isDropped() {
        ClaimSagaOrchestrationRequestEvent req = ClaimSagaOrchestrationRequestEvent.builder()
                .messageId("msg-1").action(SagaActionType.CREATE_CLAIM).build();
        when(processedMessageRepository.existsById("msg-1")).thenReturn(true);

        service.startOrchestration(req);

        verify(sagaRepository, never()).findBySagaId(anyString());
        verify(sagaRepository, never()).save(any(ClaimSagaOrchestration.class));
        verify(sagaOutboxPublisher, never()).enqueueIfAbsent(anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void startOrchestration_existingTerminalSaga_isNotRestarted() {
        ClaimSagaOrchestrationRequestEvent req = ClaimSagaOrchestrationRequestEvent.builder()
                .messageId("msg-1").sagaId("s1").action(SagaActionType.CREATE_CLAIM).build();
        ClaimSagaOrchestration existing = saga("s1", SagaOrchestrationStatus.COMPLETED, SagaStepType.NOTIFICATION);
        when(processedMessageRepository.existsById("msg-1")).thenReturn(false);
        when(sagaRepository.findBySagaId("s1")).thenReturn(Optional.of(existing));

        service.startOrchestration(req);

        verify(sagaRepository, never()).save(any(ClaimSagaOrchestration.class));
        verify(sagaOutboxPublisher, never()).enqueueIfAbsent(anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void handleStepResult_successfulNotification_completesSaga() {
        ClaimSagaOrchestration saga = saga("s1", SagaOrchestrationStatus.IN_PROGRESS, SagaStepType.NOTIFICATION);
        when(processedMessageRepository.existsById("rm-1")).thenReturn(false);
        when(sagaRepository.findBySagaId("s1")).thenReturn(Optional.of(saga));
        when(sagaRepository.save(any(ClaimSagaOrchestration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.handleStepResult(result("rm-1", "s1", SagaStepType.NOTIFICATION, true, 1));

        org.assertj.core.api.Assertions.assertThat(saga.getStatus()).isEqualTo(SagaOrchestrationStatus.COMPLETED);
        verify(sagaMetricsService).incrementCompleted();
        verify(sagaOutboxPublisher).enqueueIfAbsent(eq("s1"), eq("ClaimSagaOrchestrationResultEvent:COMPLETED"),
                eq(ClaimSagaOrchestratorService.ORCHESTRATION_RESULT_TOPIC), anyString(), anyString());
    }

    @Test
    void handleStepResult_successfulPayment_dispatchesNextStep() {
        ClaimSagaOrchestration saga = saga("s1", SagaOrchestrationStatus.IN_PROGRESS, SagaStepType.PAYMENT);
        when(processedMessageRepository.existsById("rm-1")).thenReturn(false);
        when(sagaRepository.findBySagaId("s1")).thenReturn(Optional.of(saga));
        when(sagaRepository.save(any(ClaimSagaOrchestration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.handleStepResult(result("rm-1", "s1", SagaStepType.PAYMENT, true, 1));

        verify(sagaOutboxPublisher).enqueueIfAbsent(eq("s1"),
                eq("ClaimSagaStepCommandEvent:NOTIFICATION:1"),
                eq(ClaimSagaOrchestratorService.STEP_COMMAND_TOPIC), anyString(), anyString());
    }

    @Test
    void handleStepResult_failedPayment_compensates() {
        ClaimSagaOrchestration saga = saga("s1", SagaOrchestrationStatus.IN_PROGRESS, SagaStepType.PAYMENT);
        when(processedMessageRepository.existsById("rm-2")).thenReturn(false);
        when(sagaRepository.findBySagaId("s1")).thenReturn(Optional.of(saga));
        when(sagaRepository.save(any(ClaimSagaOrchestration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.handleStepResult(result("rm-2", "s1", SagaStepType.PAYMENT, false, 2));

        org.assertj.core.api.Assertions.assertThat(saga.getStatus()).isEqualTo(SagaOrchestrationStatus.COMPENSATING);
        org.assertj.core.api.Assertions.assertThat(saga.isCompensationRequired()).isTrue();
        verify(compensationHandler).executeCompensation(eq("s1"), eq(SagaStepType.PAYMENT), eq(10L), anyString());
        verify(sagaOutboxPublisher).enqueueIfAbsent(eq("s1"),
                eq("ClaimSagaStepCommandEvent:COMPENSATION:2"),
                eq(ClaimSagaOrchestratorService.STEP_COMMAND_TOPIC), anyString(), anyString());
    }

    @Test
    void handleStepResult_failedCompensation_dispatchingRollback() {
        ClaimSagaOrchestration saga = saga("s1", SagaOrchestrationStatus.COMPENSATING, SagaStepType.COMPENSATION);
        when(processedMessageRepository.existsById("rm-3")).thenReturn(false);
        when(sagaRepository.findBySagaId("s1")).thenReturn(Optional.of(saga));
        when(sagaRepository.save(any(ClaimSagaOrchestration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.handleStepResult(result("rm-3", "s1", SagaStepType.COMPENSATION, false, 1));

        verify(sagaOutboxPublisher).enqueueIfAbsent(eq("s1"),
                eq("ClaimSagaStepCommandEvent:ROLLBACK:1"),
                eq(ClaimSagaOrchestratorService.STEP_COMMAND_TOPIC), anyString(), anyString());
    }

    @Test
    void handleStepResult_failedRollback_marksSagaFailed() {
        ClaimSagaOrchestration saga = saga("s1", SagaOrchestrationStatus.COMPENSATING, SagaStepType.ROLLBACK);
        when(processedMessageRepository.existsById("rm-4")).thenReturn(false);
        when(sagaRepository.findBySagaId("s1")).thenReturn(Optional.of(saga));
        when(sagaRepository.save(any(ClaimSagaOrchestration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.handleStepResult(result("rm-4", "s1", SagaStepType.ROLLBACK, false, 1));

        org.assertj.core.api.Assertions.assertThat(saga.getStatus()).isEqualTo(SagaOrchestrationStatus.FAILED);
        verify(sagaMetricsService).incrementFailed();
        verify(sagaOutboxPublisher).enqueueIfAbsent(eq("s1"), eq("ClaimSagaOrchestrationResultEvent:FAILED"),
                eq(ClaimSagaOrchestratorService.ORCHESTRATION_RESULT_TOPIC), anyString(), anyString());
    }

    @Test
    void handleStepResult_unknownSaga_doesNothing() {
        when(processedMessageRepository.existsById("rm-9")).thenReturn(false);
        when(sagaRepository.findBySagaId("missing")).thenReturn(Optional.empty());

        service.handleStepResult(result("rm-9", "missing", SagaStepType.PAYMENT, true, 1));

        verify(sagaRepository, never()).save(any(ClaimSagaOrchestration.class));
        verify(sagaOutboxPublisher, never()).enqueueIfAbsent(anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void handleStepResult_duplicateMessage_isDropped() {
        when(processedMessageRepository.existsById("rm-dup")).thenReturn(true);

        service.handleStepResult(result("rm-dup", "s1", SagaStepType.PAYMENT, true, 1));

        verify(sagaRepository, never()).findBySagaId(anyString());
    }

    @Test
    void handleTimedOutSagas_claimWon_marksTimedOutAndPublishesResult() {
        ClaimSagaOrchestration saga = saga("s1", SagaOrchestrationStatus.IN_PROGRESS, SagaStepType.PAYMENT);
        when(sagaRepository.findTimedOutSagas(any(), any())).thenReturn(List.of(saga));
        when(sagaRepository.markTimedOutIfStillActive(anyLong(), eq(SagaOrchestrationStatus.TIMED_OUT),
                anyString(), any(), any())).thenReturn(1);

        service.handleTimedOutSagas();

        org.assertj.core.api.Assertions.assertThat(saga.getStatus()).isEqualTo(SagaOrchestrationStatus.TIMED_OUT);
        verify(sagaMetricsService).incrementTimedOut();
        verify(sagaOutboxPublisher).enqueueIfAbsent(eq("s1"), eq("ClaimSagaOrchestrationResultEvent:TIMED_OUT"),
                eq(ClaimSagaOrchestratorService.ORCHESTRATION_RESULT_TOPIC), anyString(), anyString());
    }

    @Test
    void handleTimedOutSagas_claimLost_doesNotEmitResult() {
        ClaimSagaOrchestration saga = saga("s1", SagaOrchestrationStatus.IN_PROGRESS, SagaStepType.PAYMENT);
        when(sagaRepository.findTimedOutSagas(any(), any())).thenReturn(List.of(saga));
        when(sagaRepository.markTimedOutIfStillActive(anyLong(), eq(SagaOrchestrationStatus.TIMED_OUT),
                anyString(), any(), any())).thenReturn(0);

        service.handleTimedOutSagas();

        verify(sagaMetricsService, never()).incrementTimedOut();
        verify(sagaOutboxPublisher, never()).enqueueIfAbsent(anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void recoverFailedSagas_claimWon_dispatchesRetryStep() {
        ClaimSagaOrchestration saga = saga("s1", SagaOrchestrationStatus.FAILED, SagaStepType.PAYMENT);
        saga.setAttempts(1);
        when(sagaRepository.findRecoverableSagas(eq(SagaOrchestrationStatus.FAILED), any())).thenReturn(List.of(saga));
        when(sagaRepository.claimRecoveryForOrchestrator(eq(1L), eq(SagaOrchestrationStatus.FAILED),
                eq(SagaOrchestrationStatus.IN_PROGRESS), any(), any(), any(), anyInt())).thenReturn(1);

        service.recoverFailedSagas();

        org.assertj.core.api.Assertions.assertThat(saga.getStatus()).isEqualTo(SagaOrchestrationStatus.IN_PROGRESS);
        org.assertj.core.api.Assertions.assertThat(saga.getAttempts()).isEqualTo(2);
        verify(sagaMetricsService).incrementRecoveryRetry();
        verify(sagaOutboxPublisher).enqueueIfAbsent(eq("s1"),
                eq("ClaimSagaStepCommandEvent:PAYMENT:2"),
                eq(ClaimSagaOrchestratorService.STEP_COMMAND_TOPIC), anyString(), anyString());
    }

    @Test
    void recoverFailedSagas_noCurrentStep_skipsSaga() {
        ClaimSagaOrchestration saga = saga("s1", SagaOrchestrationStatus.FAILED, null);
        when(sagaRepository.findRecoverableSagas(eq(SagaOrchestrationStatus.FAILED), any())).thenReturn(List.of(saga));

        service.recoverFailedSagas();

        verify(sagaRepository, never()).claimRecoveryForOrchestrator(anyLong(), any(), any(), any(), any(),
                any(), anyInt());
    }
}