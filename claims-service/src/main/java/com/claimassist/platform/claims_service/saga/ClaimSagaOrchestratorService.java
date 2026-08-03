package com.claimassist.platform.claims_service.saga;

import com.claimassist.platform.claims_service.entity.ClaimSagaOrchestration;
import com.claimassist.platform.claims_service.entity.SagaProcessedMessage;
import com.claimassist.platform.claims_service.repository.ClaimSagaOrchestrationRepository;
import com.claimassist.platform.claims_service.repository.SagaProcessedMessageRepository;
import com.claimassist.platform.common_lib.enums.SagaActionType;
import com.claimassist.platform.common_lib.enums.SagaOrchestrationStatus;
import com.claimassist.platform.common_lib.enums.SagaStepType;
import com.claimassist.platform.common_lib.event.ClaimSagaOrchestrationRequestEvent;
import com.claimassist.platform.common_lib.event.ClaimSagaOrchestrationResultEvent;
import com.claimassist.platform.common_lib.event.ClaimSagaStepCommandEvent;
import com.claimassist.platform.common_lib.event.ClaimSagaStepResultEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimSagaOrchestratorService {

    public static final String ORCHESTRATION_REQUEST_TOPIC = "claim-saga-orchestration-request-event";
    public static final String STEP_COMMAND_TOPIC = "claim-saga-step-command-event";
    public static final String STEP_RESULT_TOPIC = "claim-saga-step-result-event";
    public static final String ORCHESTRATION_RESULT_TOPIC = "claim-saga-orchestration-result-event";

    private final ClaimSagaOrchestrationRepository sagaRepository;
    private final SagaProcessedMessageRepository processedMessageRepository;
    private final SagaOutboxPublisher sagaOutboxPublisher;
    private final SagaMetricsService sagaMetricsService;
    private final SagaCompensationHandler compensationHandler;
    private final SagaIdempotencyManager idempotencyManager;
    private final ObjectMapper objectMapper;

    @Value("${saga.orchestrator.timeout-seconds:180}")
    private long timeoutSeconds;

    @Value("${saga.orchestrator.recovery.retry-delay-seconds:30}")
    private long recoveryRetryDelaySeconds;

    @Value("${saga.orchestrator.recovery.max-attempts:3}")
    private int maxRecoveryAttempts;

    @Transactional
    public void startOrchestration(ClaimSagaOrchestrationRequestEvent request) {
        String messageId = ensureMessageId(request.messageId(), request.sagaId(), "request");
        if (processedMessageRepository.existsById(messageId)) {
            return;
        }

        String sagaId = request.sagaId() != null && !request.sagaId().isBlank()
                ? request.sagaId()
                : UUID.randomUUID().toString();

        ClaimSagaOrchestration saga = sagaRepository.findBySagaId(sagaId)
                .orElseGet(() -> createSaga(request, sagaId));

        processedMessageRepository.save(new SagaProcessedMessage(messageId, Instant.now()));

        if (isTerminal(saga.getStatus())) {
            return;
        }

        saga.setStatus(SagaOrchestrationStatus.IN_PROGRESS);
        saga.setExpiresAt(Instant.now().plusSeconds(timeoutSeconds));
        sagaRepository.save(saga);
        dispatchStep(saga, resolveInitialStep(saga.getAction()), Math.max(1, saga.getAttempts() + 1));
    }

    @Transactional
    public void handleStepResult(ClaimSagaStepResultEvent result) {
        String messageId = ensureMessageId(result.messageId(), result.sagaId(), "result-" + result.step());
        if (processedMessageRepository.existsById(messageId)) {
            return;
        }
        processedMessageRepository.save(new SagaProcessedMessage(messageId, Instant.now()));

        Optional<ClaimSagaOrchestration> sagaOpt = sagaRepository.findBySagaId(result.sagaId());
        if (sagaOpt.isEmpty()) {
            log.warn("Received step result for unknown saga {}", result.sagaId());
            return;
        }

        ClaimSagaOrchestration saga = sagaOpt.get();
        sagaMetricsService.recordStepLatency(result.step().name(),
                Duration.between(saga.getUpdatedAt(), Instant.now()));

        if (!result.success()) {
            saga.setAttempts(Math.max(saga.getAttempts(), result.attempt()));
            saga.setLastError(result.errorMessage());
            handleStepFailure(saga, result);
            sagaRepository.save(saga);
            return;
        }

        if (result.claimId() != null) {
            saga.setClaimId(result.claimId());
        }
        saga.setAttempts(Math.max(saga.getAttempts(), result.attempt()));
        advanceSaga(saga, result.step());
        sagaRepository.save(saga);
    }

    @Transactional
    @Scheduled(fixedDelayString = "${saga.orchestrator.timeout-scan-ms:5000}")
    public void handleTimedOutSagas() {
        List<ClaimSagaOrchestration> timedOutSagas = sagaRepository.findTimedOutSagas(
                Set.of(SagaOrchestrationStatus.IN_PROGRESS, SagaOrchestrationStatus.COMPENSATING),
                Instant.now());

        for (ClaimSagaOrchestration saga : timedOutSagas) {
            saga.setStatus(SagaOrchestrationStatus.TIMED_OUT);
            saga.setLastError("Timed out while waiting for saga step completion");
            sagaMetricsService.incrementTimedOut();
            publishFinalResult(saga, "Saga timed out");
        }
        sagaRepository.saveAll(timedOutSagas);
    }

    @Transactional
    @Scheduled(fixedDelayString = "${saga.orchestrator.recovery.scan-ms:15000}")
    public void recoverFailedSagas() {
        List<ClaimSagaOrchestration> recoverable = sagaRepository.findRecoverableSagas(
                SagaOrchestrationStatus.FAILED,
                Instant.now().minusSeconds(recoveryRetryDelaySeconds));

        for (ClaimSagaOrchestration saga : recoverable) {
            if (saga.getCurrentStep() == null || saga.getAttempts() >= maxRecoveryAttempts) {
                continue;
            }
            int nextAttempt = saga.getAttempts() + 1;
            saga.setStatus(SagaOrchestrationStatus.IN_PROGRESS);
            saga.setAttempts(nextAttempt);
            saga.setExpiresAt(Instant.now().plusSeconds(timeoutSeconds));
            sagaMetricsService.incrementRecoveryRetry();
            dispatchStep(saga, saga.getCurrentStep(), nextAttempt);
        }
        sagaRepository.saveAll(recoverable);
    }

    private ClaimSagaOrchestration createSaga(ClaimSagaOrchestrationRequestEvent request, String sagaId) {
        sagaMetricsService.incrementStarted();
        return ClaimSagaOrchestration.builder()
                .sagaId(sagaId)
                .action(request.action())
                .status(SagaOrchestrationStatus.IN_PROGRESS)
                .currentStep(resolveInitialStep(request.action()))
                .claimId(request.claimId())
                .policyId(request.policyId())
                .incidentType(request.incidentType())
                .incidentDate(request.incidentDate())
                .estimatedAmountCents(request.estimatedAmountCents())
                .actorUserId(request.actorUserId())
                .note(request.note())
                .idempotencyKey(request.idempotencyKey())
                .attempts(0)
                .compensationRequired(false)
                .expiresAt(Instant.now().plusSeconds(timeoutSeconds))
                .build();
    }

    private SagaStepType resolveInitialStep(SagaActionType action) {
        return switch (action) {
            case CREATE_CLAIM -> SagaStepType.CREATE_CLAIM;
            case APPROVE_CLAIM -> SagaStepType.APPROVE_CLAIM;
            case REJECT_CLAIM -> SagaStepType.REJECT_CLAIM;
        };
    }

    private void advanceSaga(ClaimSagaOrchestration saga, SagaStepType completedStep) {
        switch (completedStep) {
            case CREATE_CLAIM, APPROVE_CLAIM -> dispatchStep(saga, SagaStepType.PAYMENT, saga.getAttempts());
            case REJECT_CLAIM, PAYMENT -> dispatchStep(saga, SagaStepType.NOTIFICATION, saga.getAttempts());
            case NOTIFICATION -> {
                saga.setStatus(SagaOrchestrationStatus.COMPLETED);
                saga.setCurrentStep(completedStep);
                sagaMetricsService.incrementCompleted();
                publishFinalResult(saga, "Saga completed successfully");
            }
            case COMPENSATION, ROLLBACK -> {
                saga.setStatus(SagaOrchestrationStatus.COMPENSATED);
                saga.setCurrentStep(completedStep);
                sagaMetricsService.incrementCompensated();
                publishFinalResult(saga, "Saga compensated");
            }
        }
    }

    private void handleStepFailure(ClaimSagaOrchestration saga, ClaimSagaStepResultEvent result) {
        if (result.step() == SagaStepType.PAYMENT || result.step() == SagaStepType.NOTIFICATION) {
            saga.setStatus(SagaOrchestrationStatus.COMPENSATING);
            saga.setCompensationRequired(true);

            // Trigger compensation handler
            compensationHandler.executeCompensation(saga.getSagaId(), result.step(), saga.getClaimId(),
                    "Step " + result.step().name() + " failed: " + result.errorMessage());

            dispatchStep(saga, SagaStepType.COMPENSATION, saga.getAttempts());
            return;
        }

        if (result.step() == SagaStepType.COMPENSATION) {
            saga.setStatus(SagaOrchestrationStatus.COMPENSATING);
            dispatchStep(saga, SagaStepType.ROLLBACK, saga.getAttempts());
            return;
        }

        if (result.step() == SagaStepType.ROLLBACK) {
            saga.setStatus(SagaOrchestrationStatus.FAILED);
            sagaMetricsService.incrementFailed();
            publishFinalResult(saga, "Saga rollback failed: " + saga.getLastError());
            return;
        }

        saga.setStatus(SagaOrchestrationStatus.FAILED);
        sagaMetricsService.incrementFailed();
        publishFinalResult(saga, "Saga failed: " + saga.getLastError());
    }

    private void dispatchStep(ClaimSagaOrchestration saga, SagaStepType step, int attempt) {
        ClaimSagaStepCommandEvent commandEvent = ClaimSagaStepCommandEvent.builder()
                .messageId(UUID.randomUUID().toString())
                .sagaId(saga.getSagaId())
                .action(saga.getAction())
                .step(step)
                .claimId(saga.getClaimId())
                .policyId(saga.getPolicyId())
                .incidentType(saga.getIncidentType())
                .incidentDate(saga.getIncidentDate())
                .estimatedAmountCents(saga.getEstimatedAmountCents())
                .actorUserId(saga.getActorUserId())
                .note(saga.getNote())
                .idempotencyKey(saga.getIdempotencyKey())
                .attempt(attempt)
                .build();

        try {
            String eventType = "ClaimSagaStepCommandEvent:" + step.name() + ":" + attempt;
            sagaOutboxPublisher.enqueueIfAbsent(
                    saga.getSagaId(),
                    eventType,
                    STEP_COMMAND_TOPIC,
                    partitionKey(saga),
                    objectMapper.writeValueAsString(commandEvent));
            saga.setCurrentStep(step);
            saga.setStatus(saga.getStatus() == SagaOrchestrationStatus.COMPENSATING
                    ? SagaOrchestrationStatus.COMPENSATING
                    : SagaOrchestrationStatus.IN_PROGRESS);
            saga.setExpiresAt(Instant.now().plusSeconds(timeoutSeconds));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize saga step command", e);
        }
    }

    private void publishFinalResult(ClaimSagaOrchestration saga, String detail) {
        ClaimSagaOrchestrationResultEvent resultEvent = ClaimSagaOrchestrationResultEvent.builder()
                .messageId(UUID.randomUUID().toString())
                .sagaId(saga.getSagaId())
                .action(saga.getAction())
                .status(saga.getStatus())
                .claimId(saga.getClaimId())
                .detail(detail)
                .build();

        try {
            String eventType = "ClaimSagaOrchestrationResultEvent:" + saga.getStatus().name();
            sagaOutboxPublisher.enqueueIfAbsent(
                    saga.getSagaId(),
                    eventType,
                    ORCHESTRATION_RESULT_TOPIC,
                    partitionKey(saga),
                    objectMapper.writeValueAsString(resultEvent));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize saga final result", e);
        }
    }

    private boolean isTerminal(SagaOrchestrationStatus status) {
        return status == SagaOrchestrationStatus.COMPLETED
                || status == SagaOrchestrationStatus.COMPENSATED
                || status == SagaOrchestrationStatus.TIMED_OUT;
    }

    private String ensureMessageId(String messageId, String sagaId, String suffix) {
        if (messageId != null && !messageId.isBlank()) {
            return messageId;
        }
        return (sagaId != null ? sagaId : "unknown") + ":" + suffix;
    }

    private String partitionKey(ClaimSagaOrchestration saga) {
        if (saga.getClaimId() != null) {
            return "claim-" + saga.getClaimId();
        }
        return "saga-" + saga.getSagaId();
    }
}

