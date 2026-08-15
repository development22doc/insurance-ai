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
            Instant now = Instant.now();
            Instant eligibleBefore = now.minusSeconds(retryDelaySeconds);
            List<ClaimSagaOrchestration> recoverable = sagaRepository.findRecoverableSagas(
                    SagaOrchestrationStatus.FAILED,
                    eligibleBefore);

            for (ClaimSagaOrchestration saga : recoverable) {
                if (saga.getAttempts() >= maxRecoveryAttempts) {
                    log.warn("Saga {} exceeded max recovery attempts ({}), marking as unrecoverable",
                            saga.getSagaId(), maxRecoveryAttempts);
                    continue;
                }

                long delayMs = calculateBackoffDelay(saga.getAttempts());
                try {
                    // Atomic guarded claim: this saga may be picked up concurrently by the
                    // orchestrator recovery scan or a manual trigger. Only the caller that wins the
                    // DB-side claim (1 row updated) transitions it to IN_PROGRESS, increments
                    // attempts, and schedules the backoff retry. A claim of 0 rows means another
                    // writer already claimed it (or it became ineligible), so we must NOT mutate it.
                    int claimed = sagaRepository.claimRecoveryForFailureRecovery(
                            saga.getId(),
                            SagaOrchestrationStatus.FAILED,
                            SagaOrchestrationStatus.IN_PROGRESS,
                            now.plusMillis(delayMs),
                            now,
                            eligibleBefore,
                            maxRecoveryAttempts);
                    if (claimed == 1) {
                        metricsService.incrementRecoveryRetry();
                        log.info("Saga {} recovery scheduled: attempt={}, nextRetry in {}ms",
                                saga.getSagaId(), saga.getAttempts() + 1, delayMs);
                    }
                } catch (Exception e) {
                    log.error("Failed to recover saga {}", saga.getSagaId(), e);
                    recordRecoveryFailure(saga, e);
                }
            }
            // No saveAll: each transition is persisted by the guarded claim UPDATE above; a row
            // skipped (claimed == 0) preserves whatever newer state another writer committed.

        } catch (Exception e) {
            log.error("Error during saga failure recovery scan", e);
        }
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
     * Record that recovery attempt failed. Persisted atomically DB-side so concurrent writers
     * cannot lose a failure increment (avoids a stale full-row overwrite via saveAll).
     */
    private void recordRecoveryFailure(ClaimSagaOrchestration saga, Exception e) {
        int updated = sagaRepository.recordRecoveryFailureAtomic(
                saga.getId(),
                e.getMessage(),
                RECOVERY_FAILURE_CEILING,
                SagaOrchestrationStatus.FAILED,
                Instant.now());
        if (updated == 1) {
            log.error("Saga {} recovery failed, recorded failure", saga.getSagaId());
        }
    }

    /**
     * Manually trigger recovery for a specific saga. Uses the same atomic guarded claim so a
     * concurrent scheduled scan cannot also claim (and thus double-increment) the same saga.
     */
    @Transactional
    public void triggerRecovery(String sagaId) {
        sagaRepository.findBySagaId(sagaId).ifPresentOrElse(saga -> {
            if (saga.getStatus() == SagaOrchestrationStatus.FAILED) {
                long delayMs = calculateBackoffDelay(saga.getAttempts());
                int claimed = sagaRepository.claimRecoveryForFailureRecovery(
                        saga.getId(),
                        SagaOrchestrationStatus.FAILED,
                        SagaOrchestrationStatus.IN_PROGRESS,
                        Instant.now().plusMillis(delayMs),
                        Instant.now(),
                        Instant.now(),
                        maxRecoveryAttempts);
                if (claimed == 1) {
                    log.info("Manual recovery triggered for saga {}", sagaId);
                } else {
                    log.warn("Saga {} not eligible for manual recovery (already claimed or max attempts reached)",
                            sagaId);
                }
            } else {
                log.warn("Cannot recover saga {} - status is {}", sagaId, saga.getStatus());
            }
        }, () -> log.warn("Saga {} not found", sagaId));
    }

    private static final int RECOVERY_FAILURE_CEILING = 3;
}

