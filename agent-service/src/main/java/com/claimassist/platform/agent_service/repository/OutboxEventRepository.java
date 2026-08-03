package com.claimassist.platform.agent_service.repository;

import com.claimassist.platform.agent_service.entity.OutboxEvent;
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

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status AND o.nextAttemptAt <= :now ORDER BY o.createdAt ASC")
    List<OutboxEvent> findBatchForPublishing(@Param("status") OutboxStatus status, @Param("now") Instant now, Pageable pageable);

    Optional<OutboxEvent> findFirstByAggregateIdAndEventType(String aggregateId, String eventType);

    @Query("SELECT COUNT(o) FROM OutboxEvent o WHERE o.status = :status AND o.createdAt < :threshold")
    long countStalePendingEvents(@Param("status") OutboxStatus status, @Param("threshold") Instant threshold);

    @Query("SELECT COUNT(o) FROM OutboxEvent o WHERE o.status = :status AND o.createdAt < :threshold")
    long countStaleFailedEvents(@Param("status") OutboxStatus status, @Param("threshold") Instant threshold);

    @Query("SELECT COUNT(o) FROM OutboxEvent o WHERE o.status = :status")
    long countByStatus(@Param("status") OutboxStatus status);
}
