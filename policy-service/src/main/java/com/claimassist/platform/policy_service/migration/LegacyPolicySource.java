package com.claimassist.platform.policy_service.migration;

import java.util.List;

public interface LegacyPolicySource {

    List<LegacyPolicyRecord> findLegacyPolicies();
}
