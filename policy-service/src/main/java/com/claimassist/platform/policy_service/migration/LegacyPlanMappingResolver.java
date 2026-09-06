package com.claimassist.platform.policy_service.migration;

import java.util.Optional;

public interface LegacyPlanMappingResolver {

    Optional<Long> resolveTargetPlanId(LegacyPolicyRecord legacyPolicyRecord);
}
