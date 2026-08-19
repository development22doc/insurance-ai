package com.claimassist.platform.common_lib.repository;

import com.claimassist.platform.common_lib.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Base repository for Transactional Outbox pattern implementations.
 * <p>
 * Provides common query methods for outbox event management across all services.
 * Implementations should extend this interface with their service-specific OutboxEvent entity type.
 * <p>
 * This repository includes:
 * - Batch publishing with pessimistic locking to prevent concurrent processing
 * - Idempotency checks via aggregate ID and event type
 * - Stale event detection for health monitoring
 * - Status aggregation for health checks
 * <p>
 * All implementations preserve the existing pessimistic write lock strategy.
 * Locking optimization is intentionally deferred to a separate future task.
 *
 * @param <T> The service-specific OutboxEvent entity type
 */
public interface BaseOutboxEventRepository<T> extends JpaRepository<T, Long> {

    /**
     * Finds a batch of outbox events ready for publishing.
     * <p>
     * Uses pessimistic write lock to prevent concurrent publishers from processing
     * the same events. This ensures exactly-once processing semantics in distributed
     * environments where multiple publisher instances may be running.
     * <p>
     * Events are selected based on:
     * - Status matches the provided status (typically PENDING)
     * - Next attempt time is in the past or present
     * - Ordered by creation time for FIFO processing
     *
     * @param status The outbox status to filter by (typically PENDING)
     * @param now The current time for comparison with nextAttemptAt
     * @param pageable Page size and offset for batch processing
     * @return List of outbox events ready for publishing, locked for update
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM #{#entityName} o WHERE o.status = :status AND o.nextAttemptAt <= :now ORDER BY o.createdAt ASC")
    List<T> findBatchForPublishing(@Param("status") OutboxStatus status, @Param("now") Instant now, Pageable pageable);

    /**
     * Finds the first outbox event for a given aggregate ID and event type.
     * <p>
     * Used for idempotency checks to prevent duplicate event creation.
     * If an event with the same aggregate ID and event type already exists,
     * it should not be created again.
     *
     * @param aggregateId The aggregate root ID (claim ID, saga ID, etc.)
     * @param eventType The type of event for idempotency checking
     * @return Optional containing the existing event if found
     */
    Optional<T> findFirstByAggregateIdAndEventType(String aggregateId, String eventType);

    /**
     * Counts stale pending events older than the threshold.
     * <p>
     * Used for health monitoring to detect events that have been pending
     * for too long, indicating potential publishing issues.
     *
     * @param status The outbox status (typically PENDING)
     * @param threshold The time threshold; events older than this are considered stale
     * @return Count of stale pending events
     */
    @Query("SELECT COUNT(o) FROM #{#entityName} o WHERE o.status = :status AND o.createdAt < :threshold")
    long countStalePendingEvents(@Param("status") OutboxStatus status, @Param("threshold") Instant threshold);

    /**
     * Counts stale failed events older than the threshold.
     * <p>
     * Used for health monitoring to detect failed events that have not been
     * addressed for too long, requiring manual intervention.
     *
     * @param status The outbox status (typically FAILED)
     * @param threshold The time threshold; events older than this are considered stale
     * @return Count of stale failed events
     */
    @Query("SELECT COUNT(o) FROM #{#entityName} o WHERE o.status = :status AND o.createdAt < :threshold")
    long countStaleFailedEvents(@Param("status") OutboxStatus status, @Param("threshold") Instant threshold);

    /**
     * Counts all events with a given status.
     * <p>
     * Used for health monitoring to track the total number of events
     * in each status (PENDING, PUBLISHED, FAILED).
     *
     * @param status The outbox status to count
     * @return Total count of events with the given status
     */
    @Query("SELECT COUNT(o) FROM #{#entityName} o WHERE o.status = :status")
    long countByStatus(@Param("status") OutboxStatus status);
}
