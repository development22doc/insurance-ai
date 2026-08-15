package com.claimassist.platform.agent_service.consumer;

import com.claimassist.platform.agent_service.repository.AgentEventRepository;
import com.claimassist.platform.common_lib.enums.AgentEventStatus;
import com.claimassist.platform.common_lib.event.ClaimUpdateResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.observability.MDCUtility;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.common_lib.messaging.AckUtils;
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
    private final EventLogger eventLogger;

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
            @Header(name = LoggingConstants.CORRELATION_ID_HEADER, required = false) String correlationId,
            @Header(name = LoggingConstants.TRACE_ID_HEADER, required = false) String traceId,
            @Header(name = LoggingConstants.SPAN_ID_HEADER, required = false) String spanId,
            Acknowledgment ack) throws Exception {

        try {
            ClaimUpdateResponseEvent response = objectMapper.readValue(rawMessage, ClaimUpdateResponseEvent.class);

            // Populate MDC so emitted events/logs are correlated with the saga
            // Prefer correlation headers from Kafka message, fallback to sagaId
            if (correlationId != null) {
                MDCUtility.putCorrelationId(correlationId);
            } else {
                MDCUtility.putCorrelationId(response.sagaId());
            }
            if (traceId != null) {
                MDCUtility.putTraceId(traceId);
            }
            if (spanId != null) {
                MDCUtility.putSpanId(spanId);
            }

            try {
                eventLogger.logKafkaEvent(null, null, java.util.Map.of(
                        "event", "claim.update.response.received",
                        "sagaId", response.sagaId(),
                        "claimId", response.claimId(),
                        "success", response.success()
                ));
            } catch (Exception ignored) {}

            agentEventRepository.findBySagaId(response.sagaId()).ifPresentOrElse(event -> {

                if (event.getStatus() != AgentEventStatus.PENDING) {
                    log.info("Response for saga {} already handled (status={}). Skipping.", response.sagaId(), event.getStatus());
                    return;
                }

                if (response.success()) {
                    event.setStatus(AgentEventStatus.CONFIRMED);
                    try { eventLogger.logBusinessEvent(null, null, java.util.Map.of(
                            "event", "agent.saga.confirmed",
                            "sagaId", response.sagaId(),
                            "claimId", response.claimId()
                    )); } catch (Exception ignored) {}
                    log.info("Claim-update saga {} CONFIRMED", response.sagaId());
                } else {
                    event.setStatus(AgentEventStatus.FAILED);
                    event.setContent(event.getContent() + " | REJECTED: " + response.errorMessage());
                    try { eventLogger.logBusinessEvent(null, null, java.util.Map.of(
                            "event", "agent.saga.failed",
                            "sagaId", response.sagaId(),
                            "claimId", response.claimId(),
                            "error", response.errorMessage()
                    )); } catch (Exception ignored) {}
                    log.warn("Claim-update saga {} FAILED: {}", response.sagaId(), response.errorMessage());
                }
            }, () -> log.warn("Received claim-update response for unknown sagaId {} - no matching AgentEvent found", response.sagaId()));

            MDCUtility.clearAll();

            // Manual acknowledgement after successful processing. Registered as an
            // after-commit callback: the @Transactional boundary commits when this method
            // returns, so acknowledging only after commit guarantees a DB rollback leaves
            // the offset uncommitted and Kafka redelivers per at-least-once semantics.
            AckUtils.acknowledgeAfterCommit(ack);
            log.debug("Message acknowledged - topic: {}, partition: {}, offset: {}", topic, partition, offset);

        } catch (Exception e) {
            log.error("Error processing claim update response from topic: {}, partition: {}, offset: {}", topic, partition, offset, e);
            throw e;
        }
    }
}
