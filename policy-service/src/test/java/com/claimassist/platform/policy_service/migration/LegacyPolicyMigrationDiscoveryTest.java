package com.claimassist.platform.policy_service.migration;

import com.claimassist.platform.policy_service.entity.PolicyContract;
import com.claimassist.platform.policy_service.entity.PolicyLegacyIdMap;
import com.claimassist.platform.policy_service.entity.PolicyPeriod;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.claimassist.platform.policy_service.migration.LegacyPolicyMigrationDiscovery.LegacyPolicyClassification;
import static org.assertj.core.api.Assertions.assertThat;

class LegacyPolicyMigrationDiscoveryTest {

    @Test
    void deterministicSafeMatch_isAccepted() {
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

        assertThat(decision.classification()).isEqualTo(LegacyPolicyClassification.SAFE_MATCH);
        assertThat(decision.targetPolicyContractId()).isEqualTo(contract.getId());
        assertThat(decision.requiresManualAction()).isFalse();
    }

    @Test
    void ambiguousMatch_isRejected() {
        PolicyContract first = contract(42L, 100L, "POL-1001", "ACTIVE");
        PolicyContract second = contract(42L, 101L, "POL-1001", "ACTIVE");

        var decision = LegacyPolicyMigrationDiscovery.classify(
                new LegacyPolicyMigrationDiscovery.LegacyPolicyRecord(
                        LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE,
                        9002L,
                        42L,
                        "POL-1001",
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

        assertThat(decision.classification()).isEqualTo(LegacyPolicyClassification.AMBIGUOUS);
        assertThat(decision.requiresManualAction()).isTrue();
    }

    @Test
    void unmatchedCustomerRecord_staysUnmatched() {
        PolicyContract otherCustomer = contract(99L, 100L, "POL-222", "ACTIVE");
        var decision = LegacyPolicyMigrationDiscovery.classify(
                new LegacyPolicyMigrationDiscovery.LegacyPolicyRecord(
                        LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE,
                        9003L,
                        42L,
                        "POL-555",
                        "AUTO",
                        "AUTO_BASIC",
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "ACTIVE",
                        "POLICY"
                ),
                List.of(otherCustomer),
                List.of(),
                List.of());

        assertThat(decision.classification()).isEqualTo(LegacyPolicyClassification.CUSTOMER_SERVICE_ONLY);
    }

    @Test
    void customerServiceOnlyRecord_requiresReconciliation() {
        var decision = LegacyPolicyMigrationDiscovery.classify(
                new LegacyPolicyMigrationDiscovery.LegacyPolicyRecord(
                        LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE,
                        9004L,
                        42L,
                        "POL-UNMATCHED",
                        "AUTO",
                        "AUTO_BASIC",
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "ACTIVE",
                        "POLICY"
                ),
                List.of(),
                List.of(),
                List.of());

        assertThat(decision.classification()).isEqualTo(LegacyPolicyClassification.CUSTOMER_SERVICE_ONLY);
    }

    @Test
    void invalidDates_failClosed() {
        var decision = LegacyPolicyMigrationDiscovery.classify(
                new LegacyPolicyMigrationDiscovery.LegacyPolicyRecord(
                        LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE,
                        9005L,
                        42L,
                        "POL-1002",
                        "AUTO",
                        "AUTO_BASIC",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2025-01-01T00:00:00Z"),
                        "ACTIVE",
                        "POLICY"
                ),
                List.of(),
                List.of(),
                List.of());

        assertThat(decision.classification()).isEqualTo(LegacyPolicyClassification.INVALID);
    }

    @Test
    void productMismatch_isRejectedAsInvalid() {
        PolicyContract contract = contract(42L, 0L, "POL-1003", "ACTIVE");
        PolicyPeriod period = period(contract, 1L, Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));

        var decision = LegacyPolicyMigrationDiscovery.classify(
                new LegacyPolicyMigrationDiscovery.LegacyPolicyRecord(
                        LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE,
                        9006L,
                        42L,
                        "POL-1003",
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

        assertThat(decision.classification()).isEqualTo(LegacyPolicyClassification.INVALID);
    }

    @Test
    void customerMismatch_isRejected() {
        PolicyContract contract = contract(88L, 100L, "POL-1004", "ACTIVE");
        var decision = LegacyPolicyMigrationDiscovery.classify(
                new LegacyPolicyMigrationDiscovery.LegacyPolicyRecord(
                        LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE,
                        9007L,
                        42L,
                        "POL-1004",
                        "AUTO",
                        "AUTO_BASIC",
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "ACTIVE",
                        "POLICY"
                ),
                List.of(contract),
                List.of(),
                List.of());

        assertThat(decision.classification()).isEqualTo(LegacyPolicyClassification.UNMATCHED);
    }

    @Test
    void duplicateLegacyId_isRejected() {
        PolicyContract contract = contract(42L, 100L, "POL-1005", "ACTIVE");
        PolicyLegacyIdMap duplicate = new PolicyLegacyIdMap();
        duplicate.setLegacySource(LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE);
        duplicate.setLegacyPolicyId(9008L);
        duplicate.setPolicyContract(contract);
        duplicate.setLegacyRecordType(LegacyPolicyMigrationDiscovery.POLICY_RECORD_TYPE);

        var decision = LegacyPolicyMigrationDiscovery.classify(
                new LegacyPolicyMigrationDiscovery.LegacyPolicyRecord(
                        LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE,
                        9008L,
                        42L,
                        "POL-1005",
                        "AUTO",
                        "AUTO_BASIC",
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "ACTIVE",
                        "POLICY"
                ),
                List.of(contract),
                List.of(duplicate, duplicate),
                List.of());

        assertThat(decision.classification()).isEqualTo(LegacyPolicyClassification.AMBIGUOUS);
    }

    @Test
    void duplicatePolicyNumber_isDetectedAsContractInvariantFailure() {
        PolicyContract first = contract(42L, 100L, "POL-DUP", "ACTIVE");
        PolicyContract second = contract(43L, 100L, "POL-DUP", "ACTIVE");

        assertThat(LegacyPolicyMigrationDiscovery.validateContractAndPeriodInvariants(first, List.of(), List.of(first, second))).isFalse();
    }

    @Test
    void existingMapping_isAlreadyMapped() {
        PolicyContract contract = contract(42L, 100L, "POL-1006", "ACTIVE");
        PolicyLegacyIdMap map = new PolicyLegacyIdMap();
        map.setLegacySource(LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE);
        map.setLegacyPolicyId(9009L);
        map.setPolicyContract(contract);
        map.setLegacyRecordType(LegacyPolicyMigrationDiscovery.POLICY_RECORD_TYPE);

        var decision = LegacyPolicyMigrationDiscovery.classify(
                new LegacyPolicyMigrationDiscovery.LegacyPolicyRecord(
                        LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE,
                        9009L,
                        42L,
                        "POL-1006",
                        "AUTO",
                        "AUTO_BASIC",
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "ACTIVE",
                        "POLICY"
                ),
                List.of(contract),
                List.of(map),
                List.of());

        assertThat(decision.classification()).isEqualTo(LegacyPolicyClassification.ALREADY_MAPPED);
    }

    @Test
    void numericIdCollision_doesNotProveIdentity() {
        PolicyContract contract = contract(42L, 100L, "POL-1007", "ACTIVE");
        var decision = LegacyPolicyMigrationDiscovery.classify(
                new LegacyPolicyMigrationDiscovery.LegacyPolicyRecord(
                        LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE,
                        contract.getId(),
                        42L,
                        "POL-9999",
                        "AUTO",
                        "AUTO_BASIC",
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "ACTIVE",
                        "POLICY"
                ),
                List.of(contract),
                List.of(),
                List.of());

        assertThat(decision.classification()).isNotEqualTo(LegacyPolicyClassification.SAFE_MATCH);
    }

    @Test
    void claimPolicySourceAmbiguity_failsClosed() {
        var decision = LegacyPolicyMigrationDiscovery.classifyClaimPolicyId(123L, null, List.of(), List.of(), List.of());
        assertThat(decision.classification()).isEqualTo(LegacyPolicyClassification.INVALID);
    }

    @Test
    void dryRun_isDeterministicAndIdempotent() {
        PolicyContract contract = contract(42L, 100L, "POL-1008", "ACTIVE");
        var record = new LegacyPolicyMigrationDiscovery.LegacyPolicyRecord(
                LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE,
                9010L,
                42L,
                "POL-1008",
                "AUTO",
                "AUTO_BASIC",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                "ACTIVE",
                "POLICY"
        );

        var firstList = LegacyPolicyMigrationDiscovery.dryRun(List.of(record), List.of(contract), List.of(), List.of());
        var secondList = LegacyPolicyMigrationDiscovery.dryRun(List.of(record), List.of(contract), List.of(), List.of());

        assertThat(firstList).hasSize(1);
        assertThat(secondList).hasSize(1);
        assertThat(secondList.getFirst().classification()).isEqualTo(firstList.getFirst().classification());
    }

    @Test
    void contractInvariants_requirePolicyNumberAndStatus() {
        PolicyContract invalid = new PolicyContract();
        invalid.setCustomerId(42L);
        invalid.setProductId(100L);
        invalid.setPolicyNumber(" ");
        invalid.setStatus(" ");

        assertThat(LegacyPolicyMigrationDiscovery.validateContractAndPeriodInvariants(invalid, List.of(), List.of())).isFalse();
    }

    @Test
    void policyPeriodInvariants_requirePositiveRange() {
        PolicyContract contract = contract(42L, 100L, "POL-1009", "ACTIVE");
        PolicyPeriod invalidPeriod = period(contract, 1L, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(LegacyPolicyMigrationDiscovery.validateContractAndPeriodInvariants(contract, List.of(invalidPeriod), List.of(contract))).isFalse();
    }

    @Test
    void overlappingPolicyPeriods_areRejected() {
        PolicyContract contract = contract(42L, 100L, "POL-1010", "ACTIVE");
        PolicyPeriod first = period(contract, 1L, Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-12-31T00:00:00Z"));
        PolicyPeriod second = period(contract, 2L, Instant.parse("2025-06-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(LegacyPolicyMigrationDiscovery.validateContractAndPeriodInvariants(contract, List.of(first, second), List.of(contract))).isFalse();
    }

    private PolicyContract contract(Long customerId, Long productId, String policyNumber, String status) {
        PolicyContract contract = new PolicyContract();
        long deterministicId = Math.abs((customerId * 31L) + (productId * 17L) + policyNumber.hashCode()) % 1000000L;
        contract.setId(1000L + deterministicId);
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
