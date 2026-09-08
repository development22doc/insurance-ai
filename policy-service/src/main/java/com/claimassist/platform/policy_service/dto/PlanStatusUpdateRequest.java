package com.claimassist.platform.policy_service.dto;

import jakarta.validation.constraints.NotNull;

public record PlanStatusUpdateRequest(@NotNull(message = "Plan active flag is required") Boolean active) {}
