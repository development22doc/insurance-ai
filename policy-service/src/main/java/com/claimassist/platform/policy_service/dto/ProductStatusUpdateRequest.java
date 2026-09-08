package com.claimassist.platform.policy_service.dto;

import jakarta.validation.constraints.NotNull;

public record ProductStatusUpdateRequest(@NotNull(message = "Product active flag is required") Boolean active) {}
