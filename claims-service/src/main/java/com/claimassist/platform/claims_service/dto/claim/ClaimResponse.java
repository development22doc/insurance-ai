package com.claimassist.platform.claims_service.dto.claim;

public record ClaimResponse(
        Long id,
        String claimNumber,
        String status,
        String incidentType
) {}
