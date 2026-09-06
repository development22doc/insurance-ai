package com.claimassist.platform.policy_service.migration;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyPolicyDatasetDryRunServiceTest {

    private final LegacyPolicyDryRunService dryRunService = new LegacyPolicyDryRunService();

    @Test
    void dryRun_shouldAcceptDatasetSnapshotAndMaintainDeterministicClassification() {
        LegacyPolicyDataset dataset = new LegacyPolicyDataset(
                "customer_service",
                "batch-2026-09-07-001",
                Instant.parse("2026-09-07T00:00:00Z"),
                "legacy-policy-v1",
                "sha256:demo",
                List.of(
                        new LegacyPolicyRecord(
                                101L,
                                11L,
                                "POL-101",
                                5L,
                                "Basic",
                                "AUTO",
                                60000L,
                                100000L,
                                2500000L,
                                "price_101",
                                "sub_101",
                                Instant.parse("2024-01-01T00:00:00Z"),
                                Instant.parse("2025-01-01T00:00:00Z"),
                                "ACTIVE",
                                "customer_service"
                        ),
                        new LegacyPolicyRecord(
                                102L,
                                12L,
                                "POL-102",
                                99L,
                                "Legacy",
                                "AUTO",
                                70000L,
                                150000L,
                                3000000L,
                                "price_102",
                                null,
                                Instant.parse("2024-06-01T00:00:00Z"),
                                Instant.parse("2025-06-01T00:00:00Z"),
                                "PENDING",
                                "customer_service"
                        )
                )
        );

        LegacyPolicyDryRunReport report = dryRunService.dryRun(dataset, record -> record.legacyCoveragePlanId() == 5L ? Optional.of(77L) : Optional.empty());

        assertThat(report.sourceRecordCount()).isEqualTo(2);
        assertThat(report.safeToMigrateCount()).isEqualTo(1);
        assertThat(report.requiresReviewCount()).isEqualTo(1);
        assertThat(report.transformedPolicies()).hasSize(2);
        assertThat(report.transformedPolicies().get(0).targetLifecycleStatus()).isEqualTo("ACTIVE");
        assertThat(report.transformedPolicies().get(1).reasons()).anySatisfy(reason -> assertThat(reason).contains("business approval"));
    }
}
