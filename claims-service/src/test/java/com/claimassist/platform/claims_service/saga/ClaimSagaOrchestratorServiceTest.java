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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimSagaOrchestratorServiceTest {

    @Mock
    private ClaimSagaOrchestrationRepository sagaRepository;

    @Mock
    private SagaProcessedMessageRepository processedMessageRepository;

    @Mock
    private SagaOutboxPublisher sagaOutboxPublisher;

    @Mock
    private SagaMetricsService metricsService;

    @Mock
    private SagaCompensationHandler compensationHandler;

    private ClaimSagaOrchestratorService orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimSagaOrchestratorService(sagaRepository, processedMessageRepository,
                sagaOutboxPublisher, metricsService, compensationHandler, new ObjectMapper());
        ReflectionTestUtils.setField(orchestrator, "timeoutSeconds", 180L);
        ReflectionTestUtils.setField(orchestrator, "recoveryRetryDelaySeconds", 30L);
        ReflectionTestUtils.setField(orchestrator, "maxRecoveryAttempts", 3);
    }

    private ClaimSagaOrchestrationRequestEvent request() {
        return ClaimSagaOrchestrationRequestEvent.builder()
                .messageId("m1")
                .sagaId("s1")
                .action(SagaActionType.CREATE_CLAIM)
                .policyId(5L)
                .incidentType("FIRE")
                .incidentDate("2026-01-01T00:00:00Z")
                .estimatedAmountCents(1000L)
                .actorUserId(7L)
                .idempotencyKey("ik")
                .build();
    }

    private ClaimSagaOrchestration saga(SagaOrchestrationStatus status, SagaStepType step) {
        return ClaimSagaOrchestration.builder()
                .id(1L)
                .sagaId("s1")
                .action(SagaActionType.CREATE_CLAIM)
                .status(status)
                .currentStep(step)
                .claimId(100L)
                .policyId(5L)
                .attempts(0)
                .compensationRequired(false)
                .expiresAt(Instant.now().plusSeconds(180))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private ClaimSagaStepResultEvent result(String messageId, String sagaId, SagaStepType step,
                                            boolean success, int attempt) {
        return ClaimSagaStepResultEvent.builder()
                .messageId(messageId)
                .sagaId(sagaId)
                .action(SagaActionType.CREATE_CLAIM)
                .step(step)
                .success(success)
                .claimId(100L)
                .errorMessage(success ? null : "step boom")
                .attempt(attempt)
                .build();
    }

    @Test
    void startsOrchestrationAndDispatchesInitialStep() {
        when(processedMessageRepository.existsById("m1")).thenReturn(false);
        when(sagaRepository.findBySagaId("s1")).thenReturn(Optional.empty());
        when(sagaRepository.save(any(ClaimSagaOrchestration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(sagaOutboxPublisher.enqueueIfAbsent(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        orchestrator.startOrchestration(request());

        verify(sagaRepository).save(any(ClaimSagaOrchestration.class));
        verify(processedMessageRepository).save(any(SagaProcessedMessage.class));
        verify(sagaOutboxPublisher).enqueueIfAbsent(eq("s1"), anyString(),
                eq(ClaimSagaOrchestratorService.STEP_COMMAND_TOPIC), anyString(), anyString());
        verify(metricsService).incrementStarted();
    }

    @Test
    void duplicateOrchestrationRequestIsIgnored() {
        when(processedMessageRepository.existsById("m1")).thenReturn(true);

        orchestrator.startOrchestration(request());

        verify(sagaRepository, never()).save(any(ClaimSagaOrchestration.class));
        verify(sagaOutboxPublisher, never()).enqueueIfAbsent(anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void terminalSagaSkipsDispatch() {
        when(processedMessageRepository.existsById("m1")).thenReturn(false);
        when(sagaRepository.findBySagaId("s1"))
                .thenReturn(Optional.of(saga(SagaOrchestrationStatus.COMPLETED, SagaStepType.NOTIFICATION)));

        orchestrator.startOrchestration(request());

        verify(sagaOutboxPublisher, never())
                .enqueueIfAbsent(anyString(), anyString(),
                        eq(ClaimSagaOrchestratorService.STEP_COMMAND_TOPIC), anyString(), anyString());
    }

    @Test
    void successfulNotificationCompletesSaga() {
        ClaimSagaOrchestration saga = saga(SagaOrchestrationStatus.IN_PROGRESS, SagaStepType.NOTIFICATION);
        when(processedMessageRepository.existsById("r1")).thenReturn(false);
        when(sagaRepository.findBySagaId("s1")).thenReturn(Optional.of(saga));
        when(sagaRepository.save(any(ClaimSagaOrchestration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        orchestrator.handleStepResult(result("r1", "s1", SagaStepType.NOTIFICATION, true, 1));

        assertThat(saga.getStatus()).isEqualTo(SagaOrchestrationStatus.COMPLETED);
        verify(metricsService).incrementCompleted();
        verify(sagaOutboxPublisher).enqueueIfAbsent(eq("s1"), eq("ClaimSagaOrchestrationResultEvent:COMPLETED"),
                eq(ClaimSagaOrchestratorService.ORCHESTRATION_RESULT_TOPIC), anyString(), anyString());
    }

    @Test
    void successfulStepAdvancesSaga() {
        ClaimSagaOrchestration saga = saga(SagaOrchestrationStatus.IN_PROGRESS, SagaStepType.CREATE_CLAIM);
        when(processedMessageRepository.existsById("r1")).thenReturn(false);
        when(sagaRepository.findBySagaId("s1")).thenReturn(Optional.of(saga));
        when(sagaRepository.save(any(ClaimSagaOrchestration.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sagaOutboxPublisher.enqueueIfAbsent(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        orchestrator.handleStepResult(result("r1", "s1", SagaStepType.CREATE_CLAIM, true, 1));

        assertThat(saga.getCurrentStep()).isEqualTo(SagaStepType.PAYMENT);
        assertThat(saga.getStatus()).isEqualTo(SagaOrchestrationStatus.IN_PROGRESS);
        verify(sagaOutboxPublisher).enqueueIfAbsent(eq("s1"), anyString(),
                eq(ClaimSagaOrchestratorService.STEP_COMMAND_TOPIC), eq("claim-100"), anyString());
    }

    @Test
    void paymentFailureTriggersCompensation() {
        ClaimSagaOrchestration saga = saga(SagaOrchestrationStatus.IN_PROGRESS, SagaStepType.PAYMENT);
        when(processedMessageRepository.existsById("r1")).thenReturn(false);
        when(sagaRepository.findBySagaId("s1")).thenReturn(Optional.of(saga));
        when(sagaRepository.save(any(ClaimSagaOrchestration.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sagaOutboxPublisher.enqueueIfAbsent(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        orchestrator.handleStepResult(result("r1", "s1", SagaStepType.PAYMENT, false, 1));

        assertThat(saga.getStatus()).isEqualTo(SagaOrchestrationStatus.COMPENSATING);
        assertThat(saga.isCompensationRequired()).isTrue();
        verify(compensationHandler).executeCompensation(eq("s1"), eq(SagaStepType.PAYMENT), eq(100L), anyString());
        verify(sagaOutboxPublisher).enqueueIfAbsent(eq("s1"), eq("ClaimSagaStepCommandEvent:COMPENSATION:1"),
                eq(ClaimSagaOrchestratorService.STEP_COMMAND_TOPIC), anyString(), anyString());
    }

    @Test
    void notificationFailureFlowsToCompensation() {
        ClaimSagaOrchestration saga = saga(SagaOrchestrationStatus.IN_PROGRESS, SagaStepType.NOTIFICATION);
        when(processedMessageRepository.existsById("r1")).thenReturn(false);
        when(sagaRepository.findBySagaId("s1")).thenReturn(Optional.of(saga));
        when(sagaRepository.save(any(ClaimSagaOrchestration.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sagaOutboxPublisher.enqueueIfAbsent(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        orchestrator.handleStepResult(result("r1", "s1", SagaStepType.NOTIFICATION, false, 1));

        assertThat(saga.getStatus()).isEqualTo(SagaOrchestrationStatus.COMPENSATING);
        assertThat(saga.isCompensationRequired()).isTrue();
        verify(compensationHandler).executeCompensation(eq("s1"), eq(SagaStepType.NOTIFICATION), eq(100L), anyString());
    }

    @Test
    void compensationFailureDispatchesRollback() {
        ClaimSagaOrchestration saga = saga(SagaOrchestrationStatus.COMPENSATING, SagaStepType.COMPENSATION);
        when(processedMessageRepository.existsById("r1")).thenReturn(false);
        when(sagaRepository.findBySagaId("s1")).thenReturn(Optional.of(saga));
        when(sagaRepository.save(any(ClaimSagaOrchestration.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sagaOutboxPublisher.enqueueIfAbsent(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        orchestrator.handleStepResult(result("r1", "s1", SagaStepType.COMPENSATION, false, 1));

        assertThat(saga.getStatus()).isEqualTo(SagaOrchestrationStatus.COMPENSATING);
        verify(sagaOutboxPublisher).enqueueIfAbsent(eq("s1"), eq("ClaimSagaStepCommandEvent:ROLLBACK:1"),
                eq(ClaimSagaOrchestratorService.STEP_COMMAND_TOPIC), anyString(), anyString());
    }

    @Test
    void rollbackFailureMarksSagaFailed() {
        ClaimSagaOrchestration saga = saga(SagaOrchestrationStatus.COMPENSATING, SagaStepType.ROLLBACK);
        when(processedMessageRepository.existsById("r1")).thenReturn(false);
        when(sagaRepository.findBySagaId("s1")).thenReturn(Optional.of(saga));
        when(sagaRepository.save(any(ClaimSagaOrchestration.class))).thenAnswer(inv -> inv.getArgument(0));

        orchestrator.handleStepResult(result("r1", "s1", SagaStepType.ROLLBACK, false, 1));

        assertThat(saga.getStatus()).isEqualTo(SagaOrchestrationStatus.FAILED);
        verify(metricsService).incrementFailed();
        verify(sagaOutboxPublisher).enqueueIfAbsent(eq("s1"), eq("ClaimSagaOrchestrationResultEvent:FAILED"),
                eq(ClaimSagaOrchestratorService.ORCHESTRATION_RESULT_TOPIC), anyString(), anyString());
    }

    @Test
    void unknownSagaDoesNothing() {
        when(processedMessageRepository.existsById("r1")).thenReturn(false);
        when(sagaRepository.findBySagaId("missing")).thenReturn(Optional.empty());

        orchestrator.handleStepResult(result("r1", "missing", SagaStepType.PAYMENT, true, 1));

        verify(sagaRepository, never()).save(any(ClaimSagaOrchestration.class));
        verify(sagaOutboxPublisher, never()).enqueueIfAbsent(anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void duplicateStepResultIsIgnored() {
        when(processedMessageRepository.existsById("r1")).thenReturn(true);

        orchestrator.handleStepResult(result("r1", "s1", SagaStepType.PAYMENT, true, 1));

        verify(sagaRepository, never()).findBySagaId(anyString());
        verify(sagaOutboxPublisher, never()).enqueueIfAbsent(anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void timedOutSagaIsMarkedAndFinalResultPublished() {
        ClaimSagaOrchestration saga = saga(SagaOrchestrationStatus.IN_PROGRESS, SagaStepType.PAYMENT);
        saga.setExpiresAt(Instant.now().minusSeconds(10));
        when(sagaRepository.findTimedOutSagas(any(Set.class), any(Instant.class))).thenReturn(List.of(saga));
        when(sagaRepository.markTimedOutIfStillActive(eq(saga.getId()), eq(SagaOrchestrationStatus.TIMED_OUT),
                anyString(), any(Set.class), any(Instant.class))).thenReturn(1);
        when(sagaOutboxPublisher.enqueueIfAbsent(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        orchestrator.handleTimedOutSagas();

        assertThat(saga.getStatus()).isEqualTo(SagaOrchestrationStatus.TIMED_OUT);
        verify(metricsService).incrementTimedOut();
        verify(sagaOutboxPublisher).enqueueIfAbsent(eq("s1"), anyString(),
                eq(ClaimSagaOrchestratorService.ORCHESTRATION_RESULT_TOPIC), anyString(), anyString());
    }

    @Test
    void timedOutSagaClaimLostDoesNotEmitResult() {
        ClaimSagaOrchestration saga = saga(SagaOrchestrationStatus.IN_PROGRESS, SagaStepType.PAYMENT);
        saga.setExpiresAt(Instant.now().minusSeconds(10));
        when(sagaRepository.findTimedOutSagas(any(Set.class), any(Instant.class))).thenReturn(List.of(saga));
        when(sagaRepository.markTimedOutIfStillActive(eq(saga.getId()), eq(SagaOrchestrationStatus.TIMED_OUT),
                anyString(), any(Set.class), any(Instant.class))).thenReturn(0);

        orchestrator.handleTimedOutSagas();

        verify(metricsService, never()).incrementTimedOut();
        verify(sagaOutboxPublisher, never()).enqueueIfAbsent(anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void recoveredSagaDispatchesRetryStep() {
        ClaimSagaOrchestration saga = saga(SagaOrchestrationStatus.FAILED, SagaStepType.PAYMENT);
        saga.setUpdatedAt(Instant.now().minusSeconds(60));
        when(sagaRepository.findRecoverableSagas(eq(SagaOrchestrationStatus.FAILED), any(Instant.class)))
                .thenReturn(List.of(saga));
        when(sagaRepository.claimRecoveryForOrchestrator(eq(saga.getId()), eq(SagaOrchestrationStatus.FAILED),
                eq(SagaOrchestrationStatus.IN_PROGRESS), any(Instant.class), any(Instant.class), any(Instant.class),
                anyInt())).thenReturn(1);
        when(sagaOutboxPublisher.enqueueIfAbsent(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        orchestrator.recoverFailedSagas();

        assertThat(saga.getStatus()).isEqualTo(SagaOrchestrationStatus.IN_PROGRESS);
        assertThat(saga.getAttempts()).isEqualTo(1);
        verify(metricsService).incrementRecoveryRetry();
        verify(sagaOutboxPublisher).enqueueIfAbsent(eq("s1"), eq("ClaimSagaStepCommandEvent:PAYMENT:1"),
                eq(ClaimSagaOrchestratorService.STEP_COMMAND_TOPIC), anyString(), anyString());
    }

    @Test
    void recoverFailedSagasNoCurrentStepSkipsSaga() {
        ClaimSagaOrchestration saga = saga(SagaOrchestrationStatus.FAILED, null);
        when(sagaRepository.findRecoverableSagas(eq(SagaOrchestrationStatus.FAILED), any(Instant.class)))
                .thenReturn(List.of(saga));

        orchestrator.recoverFailedSagas();

        verify(sagaRepository, never()).claimRecoveryForOrchestrator(anyLong(), any(), any(), any(), any(),
                any(), anyInt());
    }
}