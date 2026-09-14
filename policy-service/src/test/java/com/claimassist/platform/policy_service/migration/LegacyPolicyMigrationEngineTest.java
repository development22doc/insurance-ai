package com.claimassist.platform.policy_service.migration;

import com.claimassist.platform.policy_service.entity.PolicyContract;
import com.claimassist.platform.policy_service.entity.PolicyLegacyIdMap;
import com.claimassist.platform.policy_service.entity.PolicyPeriod;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyPolicyMigrationEngineTest {

    @Test
    void safeMatch_isApprovedForDryRunPlanning() {
        PolicyContract contract = contract(42L, 100L, "POL-1001", "ACTIVE");
        PolicyPeriod period = period(contract, 1L, Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));

        var decision = LegacyPolicyMigrationDiscovery.classify(
                new LegacyPolicyMigrationDiscovery.LegacyPolicyRecord(
                        LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE,
                        9001L,
                        42L,
                        "POL-1001",
                        "AUTO",
                        "AUTO_BASIC",
                        Instant.parse("2025-06-01T00:00:00Z"),
                        Instant.parse("2025-12-31T23:59:59Z"),
                        "ACTIVE",
                        "POLICY"
                ),
                List.of(contract),
                List.of(),
                List.of(period));

        var plan = LegacyPolicyMigrationEngine.planLegacyMigration(
                new LegacyPolicyMigrationDiscovery.LegacyPolicyRecord(
                        LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE,
                        9001L,
                        42L,
                        "POL-1001",
                        "AUTO",
                        "AUTO_BASIC",
                        Instant.parse("2025-06-01T00:00:00Z"),
                        Instant.parse("2025-12-31T23:59:59Z"),
                        "ACTIVE",
                        "POLICY"
                ),
                List.of(contract),
                List.of(period),
                List.of());

        assertThat(decision.classification()).isEqualTo(LegacyPolicyMigrationDiscovery.LegacyPolicyClassification.SAFE_MATCH);
        assertThat(plan.allowedForAutomaticMigration()).isTrue();
        assertThat(plan.writesPlanned()).isFalse();
        assertThat(plan.targetContract()).isNotNull();
        assertThat(plan.preservedPolicyNumber()).isEqualTo("POL-1001");
    }

    @Test
    void alreadyMapped_isApprovedAsNoOpPlan() {
        PolicyContract contract = contract(42L, 100L, "POL-1002", "ACTIVE");
        PolicyLegacyIdMap mapping = new PolicyLegacyIdMap();
        mapping.setLegacySource(LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE);
        mapping.setLegacyPolicyId(9002L);
        mapping.setPolicyContract(contract);
        mapping.setLegacyRecordType(LegacyPolicyMigrationDiscovery.POLICY_RECORD_TYPE);

        var plan = LegacyPolicyMigrationEngine.planLegacyMigration(
                new LegacyPolicyMigrationDiscovery.LegacyPolicyRecord(
                        LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE,
                        9002L,
                        42L,
                        "POL-1002",
                        "AUTO",
                        "AUTO_BASIC",
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "ACTIVE",
                        "POLICY"
                ),
                List.of(contract),
                List.of(),
                List.of(mapping));

        assertThat(plan.allowedForAutomaticMigration()).isTrue();
        assertThat(plan.noOp()).isFalse();
        assertThat(plan.legacyIdMapping()).isNotNull();
    }

    @Test
    void ambiguousOrUnmatchedPolicies_areRejected() {
        PolicyContract first = contract(42L, 100L, "POL-1003", "ACTIVE");
        PolicyContract second = contract(42L, 101L, "POL-1003", "ACTIVE");

        var plan = LegacyPolicyMigrationEngine.planLegacyMigration(
                new LegacyPolicyMigrationDiscovery.LegacyPolicyRecord(
                        LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE,
                        9003L,
                        42L,
                        "POL-1003",
                        "AUTO",
                        "AUTO_BASIC",
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "ACTIVE",
                        "POLICY"
                ),
                List.of(first, second),
                List.of(),
                List.of());

        assertThat(plan.allowedForAutomaticMigration()).isFalse();
        assertThat(plan.action()).isEqualTo("REJECTED_FOR_RECONCILIATION");
    }

    @Test
    void conflictingPolicyNumbers_areRejected() {
        PolicyContract existing = contract(88L, 100L, "POL-1004", "ACTIVE");

        var plan = LegacyPolicyMigrationEngine.planLegacyMigration(
                new LegacyPolicyMigrationDiscovery.LegacyPolicyRecord(
                        LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE,
                        9004L,
                        42L,
                        "POL-1004",
                        "AUTO",
                        "AUTO_BASIC",
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "ACTIVE",
                        "POLICY"
                ),
                List.of(existing),
                List.of(),
                List.of());

        assertThat(plan.allowedForAutomaticMigration()).isFalse();
    }

    private PolicyContract contract(Long customerId, Long productId, String policyNumber, String status) {
        PolicyContract contract = new PolicyContract();
        long deterministicId = Math.abs((customerId * 31L) + (productId * 17L) + policyNumber.hashCode()) % 1000000L;
        contract.setId(2000L + deterministicId);
        contract.setCustomerId(customerId);
        contract.setProductId(productId);
        contract.setPolicyNumber(policyNumber);
        contract.setStatus(status);
        return contract;
    }

    private PolicyPeriod period(PolicyContract contract, Long planId, Instant effectiveDate, Instant expirationDate) {
        PolicyPeriod period = new PolicyPeriod();
        period.setPolicyContract(contract);
        period.setPlanId(planId);
        period.setEffectiveDate(effectiveDate);
        period.setExpirationDate(expirationDate);
        period.setStatus("ACTIVE");
        period.setRenewalSequence(0);
        return period;
    }
}
