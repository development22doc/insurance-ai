package com.claimassist.platform.claims_service.saga;

import com.claimassist.platform.claims_service.entity.ClaimSagaOrchestration;
import com.claimassist.platform.claims_service.repository.ClaimSagaOrchestrationRepository;
import com.claimassist.platform.common_lib.enums.SagaOrchestrationStatus;
import com.claimassist.platform.common_lib.enums.SagaStepType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SagaFailureRecoveryServiceTest {

    private final ClaimSagaOrchestrationRepository sagaRepository = mock(ClaimSagaOrchestrationRepository.class);
    private final ClaimSagaOrchestratorService orchestratorService = mock(ClaimSagaOrchestratorService.class);
    private final SagaMetricsService metricsService = mock(SagaMetricsService.class);

    private SagaFailureRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new SagaFailureRecoveryService(sagaRepository, orchestratorService, metricsService);
        ReflectionTestUtils.setField(service, "retryDelaySeconds", 30L);
        ReflectionTestUtils.setField(service, "maxRecoveryAttempts", 3);
        ReflectionTestUtils.setField(service, "backoffMultiplier", 2.0);
    }

    private ClaimSagaOrchestration failedSaga(long id, String sagaId, int attempts) {
        return ClaimSagaOrchestration.builder()
                .id(id)
                .sagaId(sagaId)
                .status(SagaOrchestrationStatus.FAILED)
                .currentStep(SagaStepType.CREATE_CLAIM)
                .attempts(attempts)
                .build();
    }

    @Test
    void recoverFailedSagas_eligibleSagaAndClaimWon_schedulesRetryAndIncrementsMetric() {
        ClaimSagaOrchestration saga = failedSaga(1L, "s1", 0);
        when(sagaRepository.findRecoverableSagas(eq(SagaOrchestrationStatus.FAILED), any())).thenReturn(List.of(saga));
        when(sagaRepository.claimRecoveryForFailureRecovery(
                eq(1L), eq(SagaOrchestrationStatus.FAILED), eq(SagaOrchestrationStatus.IN_PROGRESS),
                any(), any(), any(), anyInt())).thenReturn(1);

        service.recoverFailedSagas();

        verify(sagaRepository).claimRecoveryForFailureRecovery(eq(1L), eq(SagaOrchestrationStatus.FAILED),
                eq(SagaOrchestrationStatus.IN_PROGRESS), any(), any(), any(), eq(3));
        verify(metricsService).incrementRecoveryRetry();
    }

    @Test
    void recoverFailedSagas_maxAttemptsReached_skipsSagaWithoutClaiming() {
        ClaimSagaOrchestration saga = failedSaga(2L, "s2", 3);
        when(sagaRepository.findRecoverableSagas(eq(SagaOrchestrationStatus.FAILED), any())).thenReturn(List.of(saga));

        service.recoverFailedSagas();

        verify(sagaRepository, never()).claimRecoveryForFailureRecovery(
                anyLong(), any(), any(), any(), any(), any(), anyInt());
        verify(metricsService, never()).incrementRecoveryRetry();
    }

    @Test
    void recoverFailedSagas_claimLost_doesNotIncrementRetry() {
        ClaimSagaOrchestration saga = failedSaga(3L, "s3", 1);
        when(sagaRepository.findRecoverableSagas(eq(SagaOrchestrationStatus.FAILED), any())).thenReturn(List.of(saga));
        when(sagaRepository.claimRecoveryForFailureRecovery(
                eq(3L), eq(SagaOrchestrationStatus.FAILED), eq(SagaOrchestrationStatus.IN_PROGRESS),
                any(), any(), any(), anyInt())).thenReturn(0);

        service.recoverFailedSagas();

        verify(metricsService, never()).incrementRecoveryRetry();
    }

    @Test
    void recoverFailedSagas_claimThrows_recordsFailureAtomically() {
        ClaimSagaOrchestration saga = failedSaga(4L, "s4", 0);
        when(sagaRepository.findRecoverableSagas(eq(SagaOrchestrationStatus.FAILED), any())).thenReturn(List.of(saga));
        when(sagaRepository.claimRecoveryForFailureRecovery(
                eq(4L), any(), any(), any(), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("db down"));

        service.recoverFailedSagas();

        verify(sagaRepository).recordRecoveryFailureAtomic(eq(4L), any(), eq(3),
                eq(SagaOrchestrationStatus.FAILED), any());
    }

    @Test
    void recoverFailedSagas_emptyScan_doesNothing() {
        when(sagaRepository.findRecoverableSagas(eq(SagaOrchestrationStatus.FAILED), any())).thenReturn(List.of());

        service.recoverFailedSagas();

        verify(sagaRepository, never()).claimRecoveryForFailureRecovery(
                anyLong(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void calculateBackoffDelay_appliesExponentialFormula() {
        long one = ReflectionTestUtils.invokeMethod(service, "calculateBackoffDelay", 1);
        long two = ReflectionTestUtils.invokeMethod(service, "calculateBackoffDelay", 2);

        org.assertj.core.api.Assertions.assertThat(one).isEqualTo(60_000L);
        org.assertj.core.api.Assertions.assertThat(two).isEqualTo(120_000L);
    }

    @Test
    void calculateBackoffDelay_isCappedAtOneHour() {
        long delay = ReflectionTestUtils.invokeMethod(service, "calculateBackoffDelay", 20);

        org.assertj.core.api.Assertions.assertThat(delay).isEqualTo(3_600_000L);
    }

    @Test
    void triggerRecovery_failedSagaAndClaimWon_schedulesRetry() {
        ClaimSagaOrchestration saga = failedSaga(5L, "s5", 0);
        when(sagaRepository.findBySagaId("s5")).thenReturn(Optional.of(saga));
        when(sagaRepository.claimRecoveryForFailureRecovery(
                eq(5L), eq(SagaOrchestrationStatus.FAILED), eq(SagaOrchestrationStatus.IN_PROGRESS),
                any(), any(), any(), anyInt())).thenReturn(1);

        service.triggerRecovery("s5");

        verify(sagaRepository).claimRecoveryForFailureRecovery(eq(5L), eq(SagaOrchestrationStatus.FAILED),
                eq(SagaOrchestrationStatus.IN_PROGRESS), any(), any(), any(), eq(3));
    }

    @Test
    void triggerRecovery_nonFailedSaga_doesNotClaim() {
        ClaimSagaOrchestration saga = ClaimSagaOrchestration.builder()
                .id(6L).sagaId("s6")
                .status(SagaOrchestrationStatus.COMPLETED)
                .attempts(0).build();
        when(sagaRepository.findBySagaId("s6")).thenReturn(Optional.of(saga));

        service.triggerRecovery("s6");

        verify(sagaRepository, never()).claimRecoveryForFailureRecovery(
                anyLong(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void triggerRecovery_unknownSaga_doesNotClaim() {
        when(sagaRepository.findBySagaId("missing")).thenReturn(Optional.empty());

        service.triggerRecovery("missing");

        verify(sagaRepository, never()).claimRecoveryForFailureRecovery(
                anyLong(), any(), any(), any(), any(), any(), anyInt());
    }
}