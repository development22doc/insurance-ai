package com.claimassist.platform.policy_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PurchaseInitiationRequest(
        @NotNull(message = "planId is required")
        @Positive(message = "planId must be positive")
        Long planId
) {
}
