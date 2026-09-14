package com.claimassist.platform.policy_service.dto;

import java.time.Instant;

public record CustomerPolicyDetailDto(
        Long id,
        String policyNumber,
        String status,
        Instant effectiveDate,
        Instant renewalDate,
        Instant activatedAt,
        Instant cancelledAt,
        Long productId,
        String productName,
        String productType,
        Long planId,
        String planName,
        Long annualPremiumCents,
        Long deductibleCents,
        Long coverageLimitCents,
        String currency
) {
}
