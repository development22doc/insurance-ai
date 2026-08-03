package com.claimassist.platform.claims_service.saga;

import com.claimassist.platform.claims_service.entity.OutboxEvent;
import com.claimassist.platform.claims_service.repository.OutboxEventRepository;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SagaOutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;

    public boolean enqueueIfAbsent(String sagaId, String eventType, String topic, String partitionKey, String payload) {
        if (outboxEventRepository.findFirstByAggregateIdAndEventType(sagaId, eventType).isPresent()) {
            return false;
        }
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateId(sagaId)
                .eventType(eventType)
                .topic(topic)
                .partitionKey(partitionKey)
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .build();
        outboxEventRepository.save(outboxEvent);
        return true;
    }
}

