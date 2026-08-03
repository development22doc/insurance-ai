package com.claimassist.platform.common_lib.event;

import com.claimassist.platform.common_lib.enums.SagaActionType;
import com.claimassist.platform.common_lib.enums.SagaStepType;
import lombok.Builder;

@Builder
public record ClaimSagaStepCommandEvent(
        String messageId,
        String sagaId,
        SagaActionType action,
        SagaStepType step,
        Long claimId,
        Long policyId,
        String incidentType,
        String incidentDate,
        Long estimatedAmountCents,
        Long actorUserId,
        String note,
        String idempotencyKey,
        int attempt
) {
}

