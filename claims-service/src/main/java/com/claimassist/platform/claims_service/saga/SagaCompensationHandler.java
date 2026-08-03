package com.claimassist.platform.claims_service.saga;

import com.claimassist.platform.claims_service.entity.OutboxEvent;
import com.claimassist.platform.claims_service.repository.OutboxEventRepository;
import com.claimassist.platform.claims_service.service.command.ClaimCommandService;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import com.claimassist.platform.common_lib.enums.SagaStepType;
import com.claimassist.platform.common_lib.event.ClaimSagaStepCommandEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Saga compensation handler - responsible for executing compensation and rollback
 * steps when saga steps fail. Implements the Saga pattern's failure recovery mechanism.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaCompensationHandler {

    private final ClaimCommandService claimCommandService;
    private final SagaOutboxPublisher sagaOutboxPublisher;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Execute compensation logic for a failed step.
     * This reverses the effects of previous successful steps when a later step fails.
     *
     * @param sagaId The saga ID
     * @param failedStep The step that failed
     * @param claimId The claim ID being processed
     * @param compensationReason Human-readable reason for compensation
     */
    @Transactional
    public void executeCompensation(String sagaId, SagaStepType failedStep, Long claimId, String compensationReason) {
        log.info("Executing compensation for saga {}: step={}, claimId={}, reason={}",
                sagaId, failedStep, claimId, compensationReason);

        try {
            switch (failedStep) {
                case PAYMENT -> compensatePayment(sagaId, claimId);
                case NOTIFICATION -> compensateNotification(sagaId, claimId);
                case CREATE_CLAIM, APPROVE_CLAIM, REJECT_CLAIM ->
                    log.info("No compensation needed for step {}", failedStep);
                case COMPENSATION, ROLLBACK ->
                    log.error("Compensation already in progress for saga {}", sagaId);
            }
        } catch (Exception e) {
            log.error("Failed to execute compensation for saga {}", sagaId, e);
            recordCompensationFailure(sagaId, failedStep, e);
        }
    }

    /**
     * Compensate payment: if a payment was initiated but a later step failed,
     * issue a refund request.
     */
    private void compensatePayment(String sagaId, Long claimId) {
        log.info("Compensating payment for claim: {}", claimId);
        // In a real system, this would call a payment service to issue a refund
        // For now, we'll just log it
        try {
            String eventType = "PaymentCompensationEvent:" + sagaId;
            sagaOutboxPublisher.enqueueIfAbsent(
                    sagaId,
                    eventType,
                    "claim-payment-compensation",
                    "claim-" + claimId,
                    objectMapper.writeValueAsString(new CompensationEvent(
                            sagaId, claimId, "PAYMENT", Instant.now()
                    )));
            log.info("Payment compensation queued for saga {}", sagaId);
        } catch (Exception e) {
            log.error("Failed to queue payment compensation", e);
            throw new IllegalStateException("Failed to compensate payment", e);
        }
    }

    /**
     * Compensate notification: if a notification was sent but saga failed,
     * send a cancellation notification.
     */
    private void compensateNotification(String sagaId, Long claimId) {
        log.info("Compensating notification for claim: {}", claimId);
        try {
            String eventType = "NotificationCompensationEvent:" + sagaId;
            sagaOutboxPublisher.enqueueIfAbsent(
                    sagaId,
                    eventType,
                    "claim-notification-compensation",
                    "claim-" + claimId,
                    objectMapper.writeValueAsString(new CompensationEvent(
                            sagaId, claimId, "NOTIFICATION", Instant.now()
                    )));
            log.info("Notification compensation queued for saga {}", sagaId);
        } catch (Exception e) {
            log.error("Failed to queue notification compensation", e);
            throw new IllegalStateException("Failed to compensate notification", e);
        }
    }

    /**
     * Record that compensation failed - marks the saga as unrecoverable.
     */
    private void recordCompensationFailure(String sagaId, SagaStepType failedStep, Exception e) {
        try {
            String eventType = "CompensationFailureEvent:" + sagaId + ":" + failedStep.name();
            String payload = objectMapper.writeValueAsString(
                    new CompensationFailureEvent(sagaId, failedStep, e.getMessage(), Instant.now())
            );

            sagaOutboxPublisher.enqueueIfAbsent(
                    sagaId,
                    eventType,
                    "saga-compensation-failures",
                    "saga-" + sagaId,
                    payload);
        } catch (Exception ex) {
            log.error("Failed to record compensation failure", ex);
        }
    }

    /**
     * Event DTO for compensation tracking.
     */
    public record CompensationEvent(
            String sagaId,
            Long claimId,
            String compensationStep,
            Instant timestamp
    ) {}

    /**
     * Event DTO for compensation failure tracking.
     */
    public record CompensationFailureEvent(
            String sagaId,
            SagaStepType failedStep,
            String errorMessage,
            Instant timestamp
    ) {}
}

