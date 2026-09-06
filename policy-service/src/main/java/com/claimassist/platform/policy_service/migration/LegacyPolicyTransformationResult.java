package com.claimassist.platform.policy_service.migration;

import java.time.Instant;
import java.util.List;

public record LegacyPolicyTransformationResult(
        Long sourcePolicyId,
        Long targetCustomerId,
        String targetPolicyNumber,
        Long targetPlanId,
        String targetPlanName,
        String targetLifecycleStatus,
        Instant effectiveDate,
        Instant renewalDate,
        String createdAtStrategy,
        String premiumDecision,
        String coverageDecision,
        String stripeAuditDecision,
        MigrationClassification classification,
        List<String> reasons
) {
    public LegacyPolicyTransformationResult {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
