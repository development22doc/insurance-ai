package com.claimassist.platform.policy_service.migration;

import java.time.Instant;
import java.util.List;

public record MigrationPostValidationResult(
        boolean overallValid,
        int sourceCount,
        int targetCount,
        int migratedCount,
        int reconciliationCount,
        List<ValidationIssue> issues,
        Instant validatedAt
) {
    public MigrationPostValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
        validatedAt = validatedAt == null ? Instant.now() : validatedAt;
    }

    public record ValidationIssue(
            String issueType,
            String description,
            String details
    ) {
        public ValidationIssue {
            details = details == null ? "" : details;
        }
    }
}
