package com.claimassist.platform.common_lib.event;

import com.claimassist.platform.common_lib.enums.SagaActionType;
import lombok.Builder;

@Builder
public record ClaimSagaOrchestrationRequestEvent(
        String messageId,
        String sagaId,
        SagaActionType action,
        Long claimId,
        Long policyId,
        String incidentType,
        String incidentDate,
        Long estimatedAmountCents,
        Long actorUserId,
        String note,
        String idempotencyKey
) {
}

