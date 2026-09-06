package com.claimassist.platform.policy_service.migration;

import java.time.Instant;

public record LegacyPolicyRecord(
        Long legacyPolicyId,
        Long legacyCustomerId,
        String legacyPolicyNumber,
        Long legacyCoveragePlanId,
        String legacyCoveragePlanName,
        String productType,
        Long annualPremiumCents,
        Long deductibleCents,
        Long coverageLimitCents,
        String stripePriceId,
        String stripeSubscriptionId,
        Instant effectiveDate,
        Instant renewalDate,
        String legacyStatus,
        String sourceSystem
) {
    public LegacyPolicyRecord {
        sourceSystem = sourceSystem == null ? "customer_service" : sourceSystem;
    }

    public String normalizedStatus() {
        if (legacyStatus == null) {
            return "";
        }
        return legacyStatus.trim().toUpperCase();
    }
}
