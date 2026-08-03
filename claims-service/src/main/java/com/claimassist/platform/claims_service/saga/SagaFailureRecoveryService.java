package com.claimassist.platform.claims_service.saga;

import com.claimassist.platform.claims_service.entity.ClaimSagaOrchestration;
import com.claimassist.platform.claims_service.repository.ClaimSagaOrchestrationRepository;
import com.claimassist.platform.common_lib.enums.SagaOrchestrationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Saga failure recovery service - handles automatic recovery and retries
 * for failed sagas with exponential backoff and maximum retry limits.
 * Implements resilience and fault tolerance patterns.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaFailureRecoveryService {

    private final ClaimSagaOrchestrationRepository sagaRepository;
    private final ClaimSagaOrchestratorService orchestratorService;
    private final SagaMetricsService metricsService;

    @Value("${saga.recovery.retry-delay-seconds:30}")
    private long retryDelaySeconds;

    @Value("${saga.recovery.max-attempts:3}")
    private int maxRecoveryAttempts;

    @Value("${saga.recovery.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    /**
     * Scan for failed sagas that are eligible for recovery and retry them.
     * Uses exponential backoff between attempts.
     * Runs periodically as configured.
     */
    @Transactional
    @Scheduled(fixedDelayString = "${saga.recovery.scan-ms:15000}")
    public void recoverFailedSagas() {
        try {
            List<ClaimSagaOrchestration> recoverable = sagaRepository.findRecoverableSagas(
                    SagaOrchestrationStatus.FAILED,
                    Instant.now().minusSeconds(retryDelaySeconds));

            for (ClaimSagaOrchestration saga : recoverable) {
                if (saga.getAttempts() >= maxRecoveryAttempts) {
                    log.warn("Saga {} exceeded max recovery attempts ({}), marking as unrecoverable",
                            saga.getSagaId(), maxRecoveryAttempts);
                    saga.setStatus(SagaOrchestrationStatus.FAILED);
                    metricsService.incrementFailed();
                    continue;
                }

                try {
                    recoverSaga(saga);
                    metricsService.incrementRecoveryRetry();
                } catch (Exception e) {
                    log.error("Failed to recover saga {}", saga.getSagaId(), e);
                    recordRecoveryFailure(saga, e);
                }
            }

            sagaRepository.saveAll(recoverable);

        } catch (Exception e) {
            log.error("Error during saga failure recovery scan", e);
        }
    }

    /**
     * Recover a single failed saga by re-attempting from the failed step.
     */
    private void recoverSaga(ClaimSagaOrchestration saga) {
        log.info("Recovering failed saga {}: currentStep={}, attempts={}",
                saga.getSagaId(), saga.getCurrentStep(), saga.getAttempts());

        int nextAttempt = saga.getAttempts() + 1;
        
        // Calculate backoff delay
        long delayMs = calculateBackoffDelay(saga.getAttempts());
        saga.setNextRetryAt(Instant.now().plusMillis(delayMs));
        
        // Retry the failed step
        saga.setStatus(SagaOrchestrationStatus.IN_PROGRESS);
        saga.setAttempts(nextAttempt);
        saga.setLastRecoveryAttemptAt(Instant.now());

        log.info("Saga {} recovery scheduled: attempt={}, nextRetry in {}ms",
                saga.getSagaId(), nextAttempt, delayMs);
    }

    /**
     * Calculate exponential backoff delay for retries.
     * Formula: retryDelaySeconds * (backoffMultiplier ^ attemptNumber)
     */
    private long calculateBackoffDelay(int attemptNumber) {
        double delaySeconds = retryDelaySeconds * Math.pow(backoffMultiplier, attemptNumber);
        return (long) Math.min(delaySeconds * 1000, 3600000); // Cap at 1 hour
    }

    /**
     * Record that recovery attempt failed.
     */
    private void recordRecoveryFailure(ClaimSagaOrchestration saga, Exception e) {
        saga.setLastRecoveryError(e.getMessage());
        saga.setRecoveryFailureCount(saga.getRecoveryFailureCount() + 1);
        
        if (saga.getRecoveryFailureCount() >= 3) {
            log.error("Saga {} recovery failed multiple times, marking as unrecoverable", saga.getSagaId());
            saga.setStatus(SagaOrchestrationStatus.FAILED);
        }
    }

    /**
     * Manually trigger recovery for a specific saga.
     */
    @Transactional
    public void triggerRecovery(String sagaId) {
        sagaRepository.findBySagaId(sagaId).ifPresentOrElse(saga -> {
            if (saga.getStatus() == SagaOrchestrationStatus.FAILED) {
                recoverSaga(saga);
                sagaRepository.save(saga);
                log.info("Manual recovery triggered for saga {}", sagaId);
            } else {
                log.warn("Cannot recover saga {} - status is {}", sagaId, saga.getStatus());
            }
        }, () -> log.warn("Saga {} not found", sagaId));
    }
}

