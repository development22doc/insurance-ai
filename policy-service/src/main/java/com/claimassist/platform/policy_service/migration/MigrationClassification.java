package com.claimassist.platform.policy_service.migration;

public enum MigrationClassification {
    SAFE_TO_MIGRATE,
    REQUIRES_REVIEW,
    BLOCKED,
    DUPLICATE,
    UNSUPPORTED
}
