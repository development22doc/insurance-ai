package com.claimassist.platform.policy_service.dto;

import java.time.Instant;

public record PolicyPeriodDto(
        Long policyPeriodId,
        Long policyContractId,
        Long previousPolicyPeriodId,
        Integer renewalSequence,
        String status,
        Long planId,
        String planName,
        Instant effectiveDate,
        Instant expirationDate,
        Instant renewalDate,
        Instant activatedAt,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt
) {
}
