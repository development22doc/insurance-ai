package com.claimassist.platform.common_lib.event;

import com.claimassist.platform.common_lib.enums.SagaActionType;
import com.claimassist.platform.common_lib.enums.SagaOrchestrationStatus;
import lombok.Builder;

@Builder
public record ClaimSagaOrchestrationResultEvent(
        String messageId,
        String sagaId,
        SagaActionType action,
        SagaOrchestrationStatus status,
        Long claimId,
        String detail
) {
}

