package com.claimassist.platform.claims_service.saga;

import com.claimassist.platform.claims_service.entity.ClaimSagaOrchestration;
import com.claimassist.platform.claims_service.repository.ClaimSagaOrchestrationRepository;
import com.claimassist.platform.common_lib.enums.SagaActionType;
import com.claimassist.platform.common_lib.enums.SagaOrchestrationStatus;
import com.claimassist.platform.common_lib.enums.SagaStepType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SagaFailureRecoveryServiceTest {

    @Mock
    private ClaimSagaOrchestrationRepository sagaRepository;

    @Mock
    private SagaMetricsService metricsService;

    private SagaFailureRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        recoveryService = new SagaFailureRecoveryService(sagaRepository, metricsService);
        ReflectionTestUtils.setField(recoveryService, "retryDelaySeconds", 30L);
        ReflectionTestUtils.setField(recoveryService, "maxRecoveryAttempts", 3);
        ReflectionTestUtils.setField(recoveryService, "backoffMultiplier", 2.0);
    }

    private ClaimSagaOrchestration failedSaga(int attempts) {
        ClaimSagaOrchestration saga = ClaimSagaOrchestration.builder()
                .sagaId("s1")
                .action(SagaActionType.CREATE_CLAIM)
                .status(SagaOrchestrationStatus.FAILED)
                .currentStep(SagaStepType.PAYMENT)
                .claimId(100L)
                .attempts(attempts)
                .expiresAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now().minusSeconds(60))
                .build();
        saga.setId(1L);
        return saga;
    }

    @Test
    void claimsAndSchedulesRecoveryRetry() {
        ClaimSagaOrchestration saga = failedSaga(0);
        when(sagaRepository.findRecoverableSagas(eq(SagaOrchestrationStatus.FAILED), any(Instant.class)))
                .thenReturn(List.of(saga));
        when(sagaRepository.claimRecoveryForFailureRecovery(eq(1L), eq(SagaOrchestrationStatus.FAILED),
                eq(SagaOrchestrationStatus.IN_PROGRESS), any(Instant.class), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(1);

        recoveryService.recoverFailedSagas();

        verify(sagaRepository).claimRecoveryForFailureRecovery(eq(1L), eq(SagaOrchestrationStatus.FAILED),
                eq(SagaOrchestrationStatus.IN_PROGRESS), any(), any(), any(), eq(3));
        verify(metricsService).incrementRecoveryRetry();
    }

    @Test
    void skipsWhenMaxRecoveryAttemptsReached() {
        ClaimSagaOrchestration saga = failedSaga(3);
        when(sagaRepository.findRecoverableSagas(eq(SagaOrchestrationStatus.FAILED), any(Instant.class)))
                .thenReturn(List.of(saga));

        recoveryService.recoverFailedSagas();

        verify(sagaRepository, never()).claimRecoveryForFailureRecovery(
                eq(1L), any(), any(), any(), any(), any(), anyInt());
        verify(metricsService, never()).incrementRecoveryRetry();
    }

    @Test
    void doesNotIncrementWhenAnotherWriterClaimed() {
        ClaimSagaOrchestration saga = failedSaga(0);
        when(sagaRepository.findRecoverableSagas(eq(SagaOrchestrationStatus.FAILED), any(Instant.class)))
                .thenReturn(List.of(saga));
        when(sagaRepository.claimRecoveryForFailureRecovery(eq(1L), eq(SagaOrchestrationStatus.FAILED),
                eq(SagaOrchestrationStatus.IN_PROGRESS), any(Instant.class), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(0);

        recoveryService.recoverFailedSagas();

        verify(metricsService, never()).incrementRecoveryRetry();
    }

    @Test
    void triggerRecoveryOnlyClaimsFailedSagas() {
        ClaimSagaOrchestration failed = failedSaga(0);
        when(sagaRepository.findBySagaId("s1")).thenReturn(java.util.Optional.of(failed));
        when(sagaRepository.claimRecoveryForFailureRecovery(eq(1L), eq(SagaOrchestrationStatus.FAILED),
                eq(SagaOrchestrationStatus.IN_PROGRESS), any(Instant.class), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(1);

        recoveryService.triggerRecovery("s1");

        verify(sagaRepository).claimRecoveryForFailureRecovery(eq(1L), eq(SagaOrchestrationStatus.FAILED),
                eq(SagaOrchestrationStatus.IN_PROGRESS), any(Instant.class), any(Instant.class), any(Instant.class), anyInt());
    }

    @Test
    void triggerRecoveryRejectsNonFailedSagas() {
        ClaimSagaOrchestration completed = failedSaga(0);
        completed.setStatus(SagaOrchestrationStatus.COMPLETED);
        when(sagaRepository.findBySagaId("s1")).thenReturn(java.util.Optional.of(completed));

        recoveryService.triggerRecovery("s1");

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
                any(), any(), any(), any(), any(), any(), anyInt());
    }
}
