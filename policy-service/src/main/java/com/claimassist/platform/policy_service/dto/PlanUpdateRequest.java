package com.claimassist.platform.policy_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PlanUpdateRequest(
        @NotBlank(message = "Plan code is required") String code,
        @NotBlank(message = "Plan name is required") String name,
        @NotNull(message = "Deductible is required") @PositiveOrZero(message = "Deductible must be zero or positive") Long deductibleCents,
        @NotNull(message = "Coverage limit is required") @PositiveOrZero(message = "Coverage limit must be zero or positive") Long coverageLimitCents
) {}
