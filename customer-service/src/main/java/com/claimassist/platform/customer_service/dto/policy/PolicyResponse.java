package com.claimassist.platform.customer_service.dto.policy;

import java.time.Instant;

public record PolicyResponse(
        Long id,
        String policyNumber,
        String status,
        String coveragePlanName,
        String productType,
        Long annualPremiumCents,
        Long coverageLimitCents,
        Long deductibleCents,
        Instant effectiveDate,
        Instant renewalDate
) {}
