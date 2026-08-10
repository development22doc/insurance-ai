package com.claimassist.platform.claims_service.consumer;

import com.claimassist.platform.claims_service.entity.OutboxEvent;
import com.claimassist.platform.claims_service.entity.ProcessedEvent;
import com.claimassist.platform.claims_service.repository.OutboxEventRepository;
import com.claimassist.platform.claims_service.repository.ProcessedEventRepository;
import com.claimassist.platform.claims_service.security.SecurityExpressions;
import com.claimassist.platform.claims_service.service.command.ClaimCommandService;
import com.claimassist.platform.claims_service.service.command.ClaimCommands.UpdateClaimStatusCommand;
import com.claimassist.platform.common_lib.enums.ClaimPermission;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import com.claimassist.platform.common_lib.error.ClaimStateTransitionException;
import com.claimassist.platform.common_lib.event.ClaimUpdateRequestEvent;
import com.claimassist.platform.common_lib.event.ClaimUpdateResponseEvent;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.claimassist.platform.common_lib.observability.MDCUtility;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Saga participant: consumes {@link ClaimUpdateRequestEvent}, published by
 * agent-service's outbox when the AI agent's propose_claim_update tool is
 * invoked. This is the ONLY place a claim's status can change as a result of
 * something the AI agent said - and even here, the change is re-validated
 * from scratch, exactly as if a human had submitted it via the REST API:
 * <p>
 * 1. Idempotency: has this exact sagaId already been processed? (Kafka
 *    at-least-once delivery makes redelivery a real, expected possibility.)
 * 2. Authorization: does the user the agent was chatting with actually hold
 *    a ClaimRole with UPDATE_STATUS permission on this specific claim? The
 *    agent proposing a change is not, by itself, authorization to make it -
 *    this check uses the explicit {@code proposedByUserId} from the event
 *    payload, NOT a SecurityContext (a Kafka listener thread has none).
 * 3. State machine: does {@link ClaimCommandService#applyStatusChange} accept
 *    this specific transition? (Shared with the REST-driven path - see its
 *    Javadoc.)
 * <p>
 * The response - success OR a specific failure reason - is always sent back
 * via the SAME outbox pattern used everywhere else in this platform, never a
 * direct Kafka send, so the DB write ("we recorded this outcome") and the
 * "intent to notify agent-service" commit atomically.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClaimUpdateConsumer {

    private static final String RESPONSE_TOPIC = "claim-update-response-event";

    private final ClaimCommandService claimCommandService;
    private final SecurityExpressions securityExpressions;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final EventLogger eventLogger;

    @Transactional
    @KafkaListener(
            topics = "claim-update-request-event",
            groupId = "claims-group",
            containerFactory = "stringKafkaListenerContainerFactory")
    public void consumeClaimUpdateRequest(
            @Payload String rawMessage,
            @Header(name = LoggingConstants.CORRELATION_ID_HEADER, required = false) String correlationId,
            @Header(name = LoggingConstants.TRACE_ID_HEADER, required = false) String traceId,
            @Header(name = LoggingConstants.SPAN_ID_HEADER, required = false) String spanId,
            Acknowledgment ack) throws Exception {
        ClaimUpdateRequestEvent request = objectMapper.readValue(rawMessage, ClaimUpdateRequestEvent.class);

        // Populate MDC with the sagaId so downstream logs/events carry the
        // correlation identifier. Clear MDC in a finally block to avoid leakage.
        // Prefer correlation headers from Kafka message, fallback to sagaId
        if (correlationId != null) {
            MDCUtility.putCorrelationId(correlationId);
        } else {
            MDCUtility.putCorrelationId(request.sagaId());
        }
        if (traceId != null) {
            MDCUtility.putTraceId(traceId);
        }
        if (spanId != null) {
            MDCUtility.putSpanId(spanId);
        }

        long startNanos = System.nanoTime();
        try {
            // Emit a lightweight Kafka receive event for observability.
            try {
                eventLogger.logKafkaEvent(null, null, java.util.Map.of(
                        "event", "claim.update.request.received",
                        "sagaId", request.sagaId(),
                        "claimId", request.claimId(),
                        "proposedByUserId", request.proposedByUserId()
                ));
            } catch (Exception ignored) {}

            if (processedEventRepository.existsById(request.sagaId())) {
                log.info("Duplicate claim-update saga {} - re-queuing previous ACK.", request.sagaId());
                enqueueResponse(request, true, null);
                return;
            }

            try {
                boolean authorized = securityExpressions.hasPermissionForUser(
                        request.claimId(), request.proposedByUserId(), ClaimPermission.UPDATE_STATUS);

                if (!authorized) {
                    log.warn("Saga {} rejected: user {} lacks UPDATE_STATUS on claim {}",
                            request.sagaId(), request.proposedByUserId(), request.claimId());
                    processedEventRepository.save(new ProcessedEvent(request.sagaId(), LocalDateTime.now()));
                    enqueueResponse(request, false, "You do not have permission to update this claim");
                    return;
                }

                claimCommandService.applyStatusChange(new UpdateClaimStatusCommand(
                        request.claimId(),
                        request.proposedStatus(),
                        request.note(),
                        "AGENT:" + request.sagaId() // audit trail shows this was AI-agent-originated, distinct from a human userId
                ));

                processedEventRepository.save(new ProcessedEvent(request.sagaId(), LocalDateTime.now()));
                enqueueResponse(request, true, null);

             } catch (ClaimStateTransitionException e) {
                 log.info("Saga {} rejected by state machine: {}", request.sagaId(), e.getMessage());
                 processedEventRepository.save(new ProcessedEvent(request.sagaId(), LocalDateTime.now()));
                 enqueueResponse(request, false, e.getMessage());
             }
         } finally {
             long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
             ack.acknowledge();
             MDCUtility.clearAll();
         }
    }

    private void enqueueResponse(ClaimUpdateRequestEvent req, boolean success, String error) throws Exception {
        if (outboxEventRepository.findFirstByAggregateIdAndEventType(req.sagaId(), "ClaimUpdateResponseEvent").isPresent()) {
            log.info("Outbox: claim-update response for saga {} is already queued", req.sagaId());
            return;
        }

        ClaimUpdateResponseEvent response = ClaimUpdateResponseEvent.builder()
                .sagaId(req.sagaId())
                .claimId(req.claimId())
                .success(success)
                .errorMessage(error)
                .build();

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateId(req.sagaId())
                .eventType("ClaimUpdateResponseEvent")
                .topic(RESPONSE_TOPIC)
                .partitionKey("claim-" + req.claimId())
                .payload(objectMapper.writeValueAsString(response))
                .status(OutboxStatus.PENDING)
                .build();

        outboxEventRepository.save(outboxEvent);
    }
}
