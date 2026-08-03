package com.claimassist.platform.claims_service.dto.claim;

import java.time.Instant;

public record ClaimSummaryResponse(
        Long id,
        String claimNumber,
        Long policyId,
        String incidentType,
        String status,
        Long estimatedAmountCents,
        Long approvedAmountCents,
        String role,
        Instant incidentDate,
        Instant createdAt
) {}
