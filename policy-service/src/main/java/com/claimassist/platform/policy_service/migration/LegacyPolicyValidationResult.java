package com.claimassist.platform.policy_service.migration;

import java.util.ArrayList;
import java.util.List;

public record LegacyPolicyValidationResult(
        MigrationClassification classification,
        List<String> reasons,
        List<String> warnings,
        Long targetPlanId,
        String targetStatus,
        String premiumDecision,
        String stripeDecision
) {
    public LegacyPolicyValidationResult {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean isSafeToMigrate() {
        return classification == MigrationClassification.SAFE_TO_MIGRATE;
    }

    public static LegacyPolicyValidationResult of(
            MigrationClassification classification,
            String reason,
            Long targetPlanId,
            String targetStatus,
            String premiumDecision,
            String stripeDecision
    ) {
        return new LegacyPolicyValidationResult(
                classification,
                reason == null || reason.isBlank() ? List.of() : List.of(reason),
                List.of(),
                targetPlanId,
                targetStatus,
                premiumDecision,
                stripeDecision
        );
    }

    public LegacyPolicyValidationResult withReasons(List<String> additionalReasons) {
        List<String> merged = new ArrayList<>(this.reasons);
        if (additionalReasons != null) {
            merged.addAll(additionalReasons);
        }
        return new LegacyPolicyValidationResult(
                this.classification,
                merged,
                this.warnings,
                this.targetPlanId,
                this.targetStatus,
                this.premiumDecision,
                this.stripeDecision
        );
    }
}
