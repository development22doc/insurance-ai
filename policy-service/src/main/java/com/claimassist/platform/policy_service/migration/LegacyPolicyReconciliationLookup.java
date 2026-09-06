package com.claimassist.platform.policy_service.migration;

import java.util.Optional;

public interface LegacyPolicyReconciliationLookup {
    Optional<LegacyPolicyReconciliation> findByLegacyPolicyId(Long legacyPolicyId);

    Optional<LegacyPolicyReconciliation> findByTargetPolicyId(Long targetPolicyId);

    Optional<LegacyPolicyReconciliation> findByLegacyPolicyNumber(String legacyPolicyNumber);
}
