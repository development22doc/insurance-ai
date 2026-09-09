package com.claimassist.platform.policy_service.migration;

import java.util.List;

public record MigrationRecordResult(
        Long legacyPolicyId,
        Long targetPolicyId,
        Status status,
        List<String> reasons,
        String errorDetail,
        Long legacyCustomerId
) {
    public MigrationRecordResult {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public MigrationRecordResult(
            Long legacyPolicyId,
            Long targetPolicyId,
            Status status,
            List<String> reasons,
            String errorDetail) {
        this(legacyPolicyId, targetPolicyId, status, reasons, errorDetail, null);
    }

    public enum Status {
        ALREADY_MIGRATED,
        SUCCESSFULLY_MIGRATED,
        SKIPPED,
        FAILED
    }
}
