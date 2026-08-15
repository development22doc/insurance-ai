package com.claimassist.platform.claims_service.repository;

import com.claimassist.platform.claims_service.entity.ClaimSagaOrchestration;
import com.claimassist.platform.common_lib.enums.SagaOrchestrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClaimSagaOrchestrationRepository extends JpaRepository<ClaimSagaOrchestration, Long> {

    Optional<ClaimSagaOrchestration> findBySagaId(String sagaId);

    @Query("SELECT s FROM ClaimSagaOrchestration s WHERE s.status IN :statuses AND s.expiresAt <= :now")
    List<ClaimSagaOrchestration> findTimedOutSagas(@Param("statuses") Collection<SagaOrchestrationStatus> statuses,
                                                   @Param("now") Instant now);

    @Query("SELECT s FROM ClaimSagaOrchestration s WHERE s.status = :status AND s.updatedAt <= :before")
    List<ClaimSagaOrchestration> findRecoverableSagas(@Param("status") SagaOrchestrationStatus status,
                                                      @Param("before") Instant before);

    /**
     * Atomic guarded transition used by the timeout scan. Marks a saga TIMED_OUT only if it
     * is STILL in an active status and still expired at this instant. Returns the affected-row
     * count: 1 means this caller won the transition (the saga was not concurrently moved by
     * another writer, e.g. a step-result completing it), 0 means another writer already changed
     * it and it must NOT be overwritten with TIMED_OUT.
     */
    @Modifying
    @Query("UPDATE ClaimSagaOrchestration s SET s.status = :toStatus, s.lastError = :error, " +
            "s.updatedAt = :now WHERE s.id = :id AND s.status IN :activeStatuses AND s.expiresAt <= :now")
    int markTimedOutIfStillActive(@Param("id") Long id,
                                  @Param("toStatus") SagaOrchestrationStatus toStatus,
                                  @Param("error") String error,
                                  @Param("activeStatuses") Collection<SagaOrchestrationStatus> activeStatuses,
                                  @Param("now") Instant now);

    /**
     * Atomic guarded claim used by the orchestrator recovery scan. A failed saga may be picked up
     * concurrently by multiple recovery writers (this orchestrator scan, SagaFailureRecoveryService,
     * or a manual trigger). The guarded UPDATE transitions it to IN_PROGRESS and increments the
     * attempts DB-side ONLY if it is still FAILED, still eligible (not yet retried), still under the
     * max-attempts ceiling, and still has a step to dispatch. Returns the affected-row count: 1 means
     * this caller won the claim (it must dispatch the step and emit metrics), 0 means another writer
     * claimed it already or it is no longer eligible, and this caller must NOT overwrite its state.
     */
    @Modifying
    @Query("UPDATE ClaimSagaOrchestration s SET s.status = :toStatus, s.attempts = s.attempts + 1, " +
            "s.expiresAt = :expiresAt, s.updatedAt = :now " +
            "WHERE s.id = :id AND s.status = :fromStatus AND s.updatedAt <= :eligibleBefore " +
            "AND s.attempts < :maxAttempts AND s.currentStep IS NOT NULL")
    int claimRecoveryForOrchestrator(@Param("id") Long id,
                                     @Param("fromStatus") SagaOrchestrationStatus fromStatus,
                                     @Param("toStatus") SagaOrchestrationStatus toStatus,
                                     @Param("expiresAt") Instant expiresAt,
                                     @Param("now") Instant now,
                                     @Param("eligibleBefore") Instant eligibleBefore,
                                     @Param("maxAttempts") int maxAttempts);

    /**
     * Atomic guarded claim used by SagaFailureRecoveryService. Same semantics as
     * claimRecoveryForOrchestrator but schedules the backoff-based retry instead of dispatching
     * immediately, so it sets nextRetryAt and lastRecoveryAttemptAt instead of expiresAt.
     */
    @Modifying
    @Query("UPDATE ClaimSagaOrchestration s SET s.status = :toStatus, s.attempts = s.attempts + 1, " +
            "s.nextRetryAt = :nextRetryAt, s.lastRecoveryAttemptAt = :now, s.updatedAt = :now " +
            "WHERE s.id = :id AND s.status = :fromStatus AND s.updatedAt <= :eligibleBefore " +
            "AND s.attempts < :maxAttempts AND s.currentStep IS NOT NULL")
    int claimRecoveryForFailureRecovery(@Param("id") Long id,
                                        @Param("fromStatus") SagaOrchestrationStatus fromStatus,
                                        @Param("toStatus") SagaOrchestrationStatus toStatus,
                                        @Param("nextRetryAt") Instant nextRetryAt,
                                        @Param("now") Instant now,
                                        @Param("eligibleBefore") Instant eligibleBefore,
                                        @Param("maxAttempts") int maxAttempts);

    /**
     * Atomically records a recovery failure for a saga. Increments recoveryFailureCount DB-side and
     * marks it FAILED (unrecoverable) once the failure ceiling is reached. Returns affected rows.
     */
    @Modifying
    @Query("UPDATE ClaimSagaOrchestration s SET s.recoveryFailureCount = s.recoveryFailureCount + 1, " +
            "s.lastRecoveryError = :error, " +
            "s.status = CASE WHEN s.recoveryFailureCount + 1 >= :maxFailures THEN :failedStatus ELSE s.status END, " +
            "s.updatedAt = :now WHERE s.id = :id")
    int recordRecoveryFailureAtomic(@Param("id") Long id,
                                    @Param("error") String error,
                                    @Param("maxFailures") int maxFailures,
                                    @Param("failedStatus") SagaOrchestrationStatus failedStatus,
                                    @Param("now") Instant now);
}

