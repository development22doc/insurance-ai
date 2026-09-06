package com.claimassist.platform.policy_service.migration;

public record LegacyPolicyReconciliation(
        Long legacyPolicyId,
        Long targetPolicyId,
        Long legacyCustomerId,
        String legacyPolicyNumber,
        String sourceSystem
) {
}
