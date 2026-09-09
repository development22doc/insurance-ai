package com.claimassist.platform.policy_service.migration;

import java.time.Instant;
import java.util.List;

public record LegacyPolicyExecutionResult(
        int sourceRecordCount,
        int alreadyMigratedCount,
        int successfullyMigratedCount,
        int skippedCount,
        int failedCount,
        List<MigrationRecordResult> recordResults,
        Instant executedAt,
        String executionStatus
) {
    public LegacyPolicyExecutionResult {
        recordResults = recordResults == null ? List.of() : List.copyOf(recordResults);
        executedAt = executedAt == null ? Instant.now() : executedAt;
        executionStatus = executionStatus == null ? "UNKNOWN" : executionStatus;
    }

    public boolean hasFailures() {
        return failedCount > 0;
    }

    public boolean hasSuccesses() {
        return successfullyMigratedCount > 0;
    }
}
