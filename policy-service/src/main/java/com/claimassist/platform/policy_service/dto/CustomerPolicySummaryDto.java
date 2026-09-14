package com.claimassist.platform.policy_service.dto;

import java.time.Instant;

public record CustomerPolicySummaryDto(
        Long id,
        String policyNumber,
        String status,
        Instant effectiveDate,
        Instant renewalDate,
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
