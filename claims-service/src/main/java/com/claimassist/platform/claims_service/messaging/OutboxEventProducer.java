package com.claimassist.platform.claims_service.messaging;

import com.claimassist.platform.claims_service.entity.OutboxEvent;
import com.claimassist.platform.claims_service.repository.OutboxEventRepository;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Production-ready helper to create and enqueue outbox events.
 * This ensures consistent event creation across all services.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventProducer {

    private final OutboxEventRepository outboxEventRepository;

    /**
     * Enqueues an outbox event for publication to Kafka.
     * This is the primary method for all event publishing in the claims-service.
     *
     * @param aggregateId The aggregate root ID (claim ID, saga ID, etc.)
     * @param eventType   The type of event (used for idempotency and replay)
     * @param topic       The Kafka topic to publish to
     * @param partitionKey The partition key for ordering guarantee
     * @param payload     The JSON event payload
     * @return The created OutboxEvent
     */
    public OutboxEvent enqueue(String aggregateId, String eventType, String topic, String partitionKey, String payload) {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateId(aggregateId)
                .eventType(eventType)
                .topic(topic)
                .partitionKey(partitionKey)
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .build();

        OutboxEvent saved = outboxEventRepository.save(event);
        log.debug("Outbox event enqueued: aggregateId={}, eventType={}, topic={}",
                  aggregateId, eventType, topic);
        return saved;
    }

    /**
     * Enqueues an outbox event only if it doesn't already exist (idempotent).
     * Useful for commands that might be retried.
     *
     * @param aggregateId The aggregate root ID
     * @param eventType   The type of event
     * @param topic       The Kafka topic
     * @param partitionKey The partition key
     * @param payload     The JSON payload
     * @return true if the event was newly created, false if it already existed
     */
    public boolean enqueueIfAbsent(String aggregateId, String eventType, String topic,
                                   String partitionKey, String payload) {
        if (outboxEventRepository.findFirstByAggregateIdAndEventType(aggregateId, eventType).isPresent()) {
            log.debug("Outbox event already exists: aggregateId={}, eventType={}", aggregateId, eventType);
            return false;
        }

        enqueue(aggregateId, eventType, topic, partitionKey, payload);
        return true;
    }

    /**
     * Retrieves an outbox event for inspection (e.g., for debugging or status checks).
     *
     * @param aggregateId The aggregate root ID
     * @param eventType   The event type
     * @return The OutboxEvent if found
     */
    public boolean exists(String aggregateId, String eventType) {
        return outboxEventRepository.findFirstByAggregateIdAndEventType(aggregateId, eventType).isPresent();
    }
}

