package com.claimassist.platform.claims_service.dto.claim;

import jakarta.validation.constraints.NotBlank;

public record UpdateClaimStatusRequest(
        @NotBlank(message = "status is required") String status,
        String note
) {}
