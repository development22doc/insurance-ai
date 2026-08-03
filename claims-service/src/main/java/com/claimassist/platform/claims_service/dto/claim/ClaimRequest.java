package com.claimassist.platform.claims_service.dto.claim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.Instant;

public record ClaimRequest(
        @NotNull(message = "policyId is required") Long policyId,
        @NotBlank(message = "incidentType is required") String incidentType,
        @NotNull @PastOrPresent(message = "incidentDate cannot be in the future") Instant incidentDate,
        Long estimatedAmountCents
) {}
