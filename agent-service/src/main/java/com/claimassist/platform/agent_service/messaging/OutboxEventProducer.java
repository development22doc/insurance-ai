package com.claimassist.platform.agent_service.messaging;

import com.claimassist.platform.agent_service.entity.OutboxEvent;
import com.claimassist.platform.agent_service.repository.OutboxEventRepository;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Production-ready helper to create and enqueue outbox events.
 * This ensures consistent event creation across all services.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventProducer {

    private final OutboxEventRepository outboxEventRepository;
    private final EventLogger eventLogger;

    /**
     * Enqueues an outbox event for publication to Kafka.
     * This is the primary method for all event publishing in the agent-service.
     * Captures correlation context from MDC at event creation time so it can be
     * propagated to Kafka message headers when publishing.
     *
     * @param aggregateId The aggregate root ID (event ID, saga ID, etc.)
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
                // Capture correlation context from MDC at event creation time
                .correlationId(MDC.get(LoggingConstants.MDC_CORRELATION_ID))
                .traceId(MDC.get(LoggingConstants.MDC_TRACE_ID))
                .spanId(MDC.get(LoggingConstants.MDC_SPAN_ID))
                .build();

        OutboxEvent saved = outboxEventRepository.save(event);
        try {
            eventLogger.logKafkaEvent(null, null, java.util.Map.of(
                    "event", "outbox.event.enqueued",
                    "aggregateId", aggregateId,
                    "eventType", eventType,
                    "topic", topic,
                    "correlationId", event.getCorrelationId() != null ? event.getCorrelationId() : "none"
            ));
        } catch (Exception ignored) {}
        log.debug("Outbox event enqueued: aggregateId={}, eventType={}, topic={}, correlationId={}",
                  aggregateId, eventType, topic, event.getCorrelationId());
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
     * @return true if the event exists
     */
    public boolean exists(String aggregateId, String eventType) {
        return outboxEventRepository.findFirstByAggregateIdAndEventType(aggregateId, eventType).isPresent();
    }
}

