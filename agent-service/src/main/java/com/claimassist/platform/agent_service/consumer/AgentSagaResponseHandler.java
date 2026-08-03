package com.claimassist.platform.agent_service.consumer;

import com.claimassist.platform.agent_service.repository.AgentEventRepository;
import com.claimassist.platform.common_lib.enums.AgentEventStatus;
import com.claimassist.platform.common_lib.event.ClaimUpdateResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga participant: consumes {@link ClaimUpdateResponseEvent}, published by
 * claims-service's outbox, and resolves the matching AgentEvent (found by
 * sagaId) from PENDING to CONFIRMED/FAILED. This is what lets the agent
 * later say "your claim was moved to DOCS_REQUESTED" or "I wasn't able to
 * make that change because..." in a FOLLOWING turn, grounded in the real
 * outcome rather than assuming its own proposal succeeded.
 * <p>
 * Idempotent: if the AgentEvent has already left PENDING (a duplicate
 * delivery, which Kafka's at-least-once guarantee makes possible), this is a
 * no-op - never re-applies or "un-confirms" a terminal state.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentSagaResponseHandler {

    private final AgentEventRepository agentEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(
            topics = "claim-update-response-event",
            groupId = "agent-group",
            containerFactory = "stringKafkaListenerContainerFactory")
    public void handleClaimUpdateResponse(
            @Payload String rawMessage,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(name = "kafka_receivedPartitionId", required = false) Integer partition,
            @Header(name = "kafka_offset", required = false) Long offset,
            Acknowledgment ack) throws Exception {

        try {
            ClaimUpdateResponseEvent response = objectMapper.readValue(rawMessage, ClaimUpdateResponseEvent.class);

            agentEventRepository.findBySagaId(response.sagaId()).ifPresentOrElse(event -> {

                if (event.getStatus() != AgentEventStatus.PENDING) {
                    log.info("Response for saga {} already handled (status={}). Skipping.", response.sagaId(), event.getStatus());
                    return;
                }

                if (response.success()) {
                    event.setStatus(AgentEventStatus.CONFIRMED);
                    log.info("Claim-update saga {} CONFIRMED", response.sagaId());
                } else {
                    event.setStatus(AgentEventStatus.FAILED);
                    event.setContent(event.getContent() + " | REJECTED: " + response.errorMessage());
                    log.warn("Claim-update saga {} FAILED: {}", response.sagaId(), response.errorMessage());
                }
            }, () -> log.warn("Received claim-update response for unknown sagaId {} - no matching AgentEvent found", response.sagaId()));

            // Manual acknowledgement after successful processing
            ack.acknowledge();
            log.debug("Message acknowledged - topic: {}, partition: {}, offset: {}", topic, partition, offset);

        } catch (Exception e) {
            log.error("Error processing claim update response from topic: {}, partition: {}, offset: {}", topic, partition, offset, e);
            throw e;
        }
    }
}
