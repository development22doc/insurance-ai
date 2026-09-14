package com.claimassist.platform.policy_service.dto;

import java.time.Instant;

public record PolicyContractSummaryDto(
        Long id,
        String policyNumber,
        String status,
        Long currentPolicyPeriodId,
        Instant effectiveDate,
        Instant expirationDate,
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
