package com.claimassist.platform.policy_service.migration;

import java.time.Instant;
import java.util.List;

public record LegacyPolicyDryRunReport(
        int sourceRecordCount,
        int safeToMigrateCount,
        int requiresReviewCount,
        int duplicateCount,
        int blockedCount,
        int unsupportedCount,
        List<LegacyPolicyTransformationResult> transformedPolicies,
        Instant generatedAt,
        String checksumStatus,
        boolean datasetReady
) {
    public LegacyPolicyDryRunReport {
        transformedPolicies = transformedPolicies == null ? List.of() : List.copyOf(transformedPolicies);
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        checksumStatus = checksumStatus == null ? "NOT_APPLICABLE" : checksumStatus;
    }

    public boolean hasBlockingIssues() {
        return blockedCount > 0 || duplicateCount > 0 || unsupportedCount > 0 || !datasetReady;
    }

    public boolean isReadyForMigration() {
        return datasetReady && !hasBlockingIssues() && safeToMigrateCount > 0;
    }
}
