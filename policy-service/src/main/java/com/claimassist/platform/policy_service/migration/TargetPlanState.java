package com.claimassist.platform.policy_service.migration;

public record TargetPlanState(Long planId, boolean exists, boolean active) {
    public static TargetPlanState missing(Long planId) {
        return new TargetPlanState(planId, false, false);
    }
}
