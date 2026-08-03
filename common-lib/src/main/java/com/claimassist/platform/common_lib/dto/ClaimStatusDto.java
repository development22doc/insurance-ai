package com.claimassist.platform.common_lib.dto;

import java.util.List;

public record ClaimStatusDto(
        Long claimId,
        Long policyId,
        String claimNumber,
        String status,
        String incidentType,
        Long estimatedAmountCents,
        Long approvedAmountCents,
        List<StatusHistoryEntry> history
) {
    public record StatusHistoryEntry(String fromStatus, String toStatus, String changedBy, String changedAt) {}
}
