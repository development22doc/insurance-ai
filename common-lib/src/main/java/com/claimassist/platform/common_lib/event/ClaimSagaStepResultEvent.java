package com.claimassist.platform.common_lib.event;

import com.claimassist.platform.common_lib.enums.SagaActionType;
import com.claimassist.platform.common_lib.enums.SagaStepType;
import lombok.Builder;

@Builder
public record ClaimSagaStepResultEvent(
        String messageId,
        String sagaId,
        SagaActionType action,
        SagaStepType step,
        boolean success,
        Long claimId,
        String errorMessage,
        int attempt
) {
}

