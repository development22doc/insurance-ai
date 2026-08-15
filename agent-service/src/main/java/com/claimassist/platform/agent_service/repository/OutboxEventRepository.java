package com.claimassist.platform.agent_service.repository;

import com.claimassist.platform.agent_service.entity.OutboxEvent;
import com.claimassist.platform.common_lib.repository.BaseOutboxEventRepository;

/**
 * Agent-service specific OutboxEvent repository.
 * <p>
 * Extends the base outbox repository with agent-specific OutboxEvent entity type.
 * All common query methods are inherited from BaseOutboxEventRepository.
 * <p>
 * This repository maintains the existing pessimistic write lock strategy
 * for batch publishing to prevent concurrent processing issues.
 */
public interface OutboxEventRepository extends BaseOutboxEventRepository<OutboxEvent> {
    // All common methods inherited from BaseOutboxEventRepository:
    // - findBatchForPublishing (with pessimistic write lock)
    // - findFirstByAggregateIdAndEventType
    // - countStalePendingEvents
    // - countStaleFailedEvents
    // - countByStatus
}
