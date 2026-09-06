package com.claimassist.platform.policy_service.migration;

import java.util.Optional;

public interface TargetPlanLookup {
    Optional<TargetPlanState> findById(Long planId);
}
