package com.claimassist.platform.policy_service.dto;

import jakarta.validation.constraints.NotBlank;

public record ProductUpdateRequest(
        @NotBlank(message = "Product code is required") String code,
        @NotBlank(message = "Product name is required") String name
) {}
