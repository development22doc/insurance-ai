package com.claimassist.platform.policy_service.migration;

import java.util.ArrayList;
import java.util.List;

public record LegacyPolicyMigrationExecutionResult(
        LegacyPolicyMigrationExecutionMode mode,
        int discovered,
        int safe,
        int alreadyMapped,
        int planned,
        int migrated,
        int blocked,
        int ambiguous,
        int unmatched,
        int invalid,
        int customerServiceOnly,
        int failed,
        List<LegacyPolicyMigrationOutcome> outcomes) {

    public static LegacyPolicyMigrationExecutionResult empty(LegacyPolicyMigrationExecutionMode mode) {
        return new LegacyPolicyMigrationExecutionResult(mode, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, new ArrayList<>());
    }
}

record LegacyPolicyMigrationOutcome(
        String source,
        Long legacyPolicyId,
        LegacyPolicyMigrationDiscovery.LegacyPolicyClassification classification,
        String outcome,
        Long targetContractId,
        String targetPolicyNumber,
        Long targetPeriodId,
        String mappingStatus,
        List<String> validationFailures,
        String reason,
        boolean alreadyMapped,
        boolean persisted) {
}
