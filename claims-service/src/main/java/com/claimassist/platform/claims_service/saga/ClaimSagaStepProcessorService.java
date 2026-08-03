package com.claimassist.platform.claims_service.saga;

import com.claimassist.platform.claims_service.dto.claim.ClaimResponse;
import com.claimassist.platform.claims_service.entity.Claim;
import com.claimassist.platform.claims_service.service.command.ClaimCommandService;
import com.claimassist.platform.claims_service.service.command.ClaimCommands.SubmitClaimCommand;
import com.claimassist.platform.claims_service.service.command.ClaimCommands.UpdateClaimStatusCommand;
import com.claimassist.platform.common_lib.enums.SagaStepType;
import com.claimassist.platform.common_lib.event.ClaimSagaStepCommandEvent;
import com.claimassist.platform.common_lib.event.ClaimSagaStepResultEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimSagaStepProcessorService {

    private final ClaimCommandService claimCommandService;
    private final SagaOutboxPublisher sagaOutboxPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processStep(ClaimSagaStepCommandEvent command) {
        try {
            ClaimSagaStepResultEvent result = execute(command);
            publishResult(command.sagaId(), result);
        } catch (Exception e) {
            ClaimSagaStepResultEvent failedResult = ClaimSagaStepResultEvent.builder()
                    .messageId(UUID.randomUUID().toString())
                    .sagaId(command.sagaId())
                    .action(command.action())
                    .step(command.step())
                    .success(false)
                    .claimId(command.claimId())
                    .errorMessage(e.getMessage())
                    .attempt(command.attempt())
                    .build();
            publishResult(command.sagaId(), failedResult);
        }
    }

    private ClaimSagaStepResultEvent execute(ClaimSagaStepCommandEvent command) {
        return switch (command.step()) {
            case CREATE_CLAIM -> executeCreateClaim(command);
            case APPROVE_CLAIM -> executeStatusUpdate(command, "APPROVED");
            case REJECT_CLAIM -> executeStatusUpdate(command, "DENIED");
            case PAYMENT, NOTIFICATION, COMPENSATION, ROLLBACK -> executeSideEffectStep(command);
        };
    }

    private ClaimSagaStepResultEvent executeCreateClaim(ClaimSagaStepCommandEvent command) {
        ClaimResponse response = claimCommandService.submitClaim(new SubmitClaimCommand(
                command.policyId(),
                command.incidentType(),
                Instant.parse(command.incidentDate()),
                command.estimatedAmountCents(),
                command.actorUserId(),
                command.idempotencyKey()));
        return successResult(command, response.id(), null);
    }

    private ClaimSagaStepResultEvent executeStatusUpdate(ClaimSagaStepCommandEvent command, String newStatus) {
        Claim claim = claimCommandService.applyStatusChange(new UpdateClaimStatusCommand(
                command.claimId(),
                newStatus,
                command.note(),
                "SAGA:" + command.sagaId()));
        return successResult(command, claim.getId(), null);
    }

    private ClaimSagaStepResultEvent executeSideEffectStep(ClaimSagaStepCommandEvent command) {
        log.info("Saga {} step {} handled as orchestration side-effect placeholder", command.sagaId(), command.step());
        return successResult(command, command.claimId(), null);
    }

    private ClaimSagaStepResultEvent successResult(ClaimSagaStepCommandEvent command, Long claimId, String error) {
        return ClaimSagaStepResultEvent.builder()
                .messageId(UUID.randomUUID().toString())
                .sagaId(command.sagaId())
                .action(command.action())
                .step(command.step())
                .success(true)
                .claimId(claimId)
                .errorMessage(error)
                .attempt(command.attempt())
                .build();
    }

    private void publishResult(String sagaId, ClaimSagaStepResultEvent result) {
        try {
            String eventType = "ClaimSagaStepResultEvent:" + result.step().name() + ":" + result.attempt();
            sagaOutboxPublisher.enqueueIfAbsent(
                    sagaId,
                    eventType,
                    ClaimSagaOrchestratorService.STEP_RESULT_TOPIC,
                    result.claimId() != null ? "claim-" + result.claimId() : "saga-" + sagaId,
                    objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize saga step result", e);
        }
    }
}

