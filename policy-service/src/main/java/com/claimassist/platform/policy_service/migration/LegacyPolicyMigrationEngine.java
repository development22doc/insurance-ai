package com.claimassist.platform.policy_service.migration;

import com.claimassist.platform.policy_service.entity.PolicyContract;
import com.claimassist.platform.policy_service.entity.PolicyLegacyIdMap;
import com.claimassist.platform.policy_service.entity.PolicyPeriod;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public final class LegacyPolicyMigrationEngine {

    private LegacyPolicyMigrationEngine() {
    }

    public static LegacyPolicyMigrationPlan planLegacyMigration(
            LegacyPolicyMigrationDiscovery.LegacyPolicyRecord legacyPolicy,
            List<PolicyContract> policyContracts,
            List<PolicyPeriod> policyPeriods,
            List<PolicyLegacyIdMap> existingMappings) {

        if (legacyPolicy == null) {
            return LegacyPolicyMigrationPlan.rejected(
                    null,
                    "Legacy policy input is required for migration planning.");
        }

        LegacyPolicyMigrationDiscovery.LegacyPolicyMigrationDecision decision =
                LegacyPolicyMigrationDiscovery.classify(legacyPolicy, policyContracts, existingMappings, policyPeriods);

        if (decision == null) {
            return LegacyPolicyMigrationPlan.rejected(
                    null,
                    "Classification could not be produced for the legacy policy record.");
        }

        if (!isAllowedClassification(decision.classification())) {
            return LegacyPolicyMigrationPlan.rejected(
                    decision,
                    "Classification " + decision.classification() + " is not permitted for automatic migration execution.");
        }

        String canonicalPolicyNumber = validatePolicyNumber(decision, legacyPolicy, policyContracts);
        if (canonicalPolicyNumber == null) {
            return LegacyPolicyMigrationPlan.rejected(
                    decision,
                    "Legacy policy number is missing, conflicting, or not safe to preserve for migration.");
        }

        PolicyContract targetContract = resolveTargetContract(decision, policyContracts);
        if (targetContract == null) {
            return LegacyPolicyMigrationPlan.rejected(
                    decision,
                    "The migration decision has no deterministic PolicyContract target for execution.");
        }

        if (!LegacyPolicyMigrationDiscovery.validateContractAndPeriodInvariants(targetContract, policyPeriods, policyContracts)) {
            return LegacyPolicyMigrationPlan.rejected(
                    decision,
                    "Target PolicyContract or PolicyPeriod invariants are invalid and block automatic migration.");
        }

        PolicyPeriod initialPeriod = resolveInitialPeriod(targetContract, policyPeriods);
        PolicyLegacyIdMap mapping = buildMapping(targetContract, legacyPolicy);

        List<String> auditTrail = new ArrayList<>();
        auditTrail.add("source=" + legacyPolicy.legacySource());
        auditTrail.add("legacyPolicyId=" + legacyPolicy.legacyPolicyId());
        auditTrail.add("customerId=" + targetContract.getCustomerId());
        auditTrail.add("targetPolicyContractId=" + targetContract.getId());
        auditTrail.add("policyNumber=" + canonicalPolicyNumber);
        auditTrail.add("safeClassification=" + decision.classification());

        return new LegacyPolicyMigrationPlan(
                decision,
                true,
                false,
                false,
                "AUTOMATIC_MIGRATION_APPROVED",
                targetContract,
                initialPeriod,
                mapping,
                canonicalPolicyNumber,
                auditTrail
        );
    }

    public static boolean isAllowedClassification(LegacyPolicyMigrationDiscovery.LegacyPolicyClassification classification) {
        return classification == LegacyPolicyMigrationDiscovery.LegacyPolicyClassification.SAFE_MATCH
                || classification == LegacyPolicyMigrationDiscovery.LegacyPolicyClassification.ALREADY_MAPPED;
    }

    private static String validatePolicyNumber(
            LegacyPolicyMigrationDiscovery.LegacyPolicyMigrationDecision decision,
            LegacyPolicyMigrationDiscovery.LegacyPolicyRecord legacyPolicy,
            List<PolicyContract> policyContracts) {

        if (legacyPolicy == null || legacyPolicy.policyNumber() == null || legacyPolicy.policyNumber().isBlank()) {
            return null;
        }

        if (policyContracts == null) {
            return legacyPolicy.policyNumber();
        }

        for (PolicyContract existing : policyContracts) {
            if (existing == null || existing.getPolicyNumber() == null) {
                continue;
            }
            if (existing.getPolicyNumber().equals(legacyPolicy.policyNumber())
                    && !Objects.equals(existing.getCustomerId(), legacyPolicy.customerId())) {
                return null;
            }
        }

        return legacyPolicy.policyNumber();
    }

    private static PolicyContract resolveTargetContract(
            LegacyPolicyMigrationDiscovery.LegacyPolicyMigrationDecision decision,
            List<PolicyContract> policyContracts) {

        if (decision == null || decision.targetPolicyContractId() == null) {
            return null;
        }

        if (policyContracts == null) {
            return null;
        }

        return policyContracts.stream()
                .filter(Objects::nonNull)
                .filter(contract -> Objects.equals(contract.getId(), decision.targetPolicyContractId()))
                .findFirst()
                .orElse(null);
    }

    private static PolicyPeriod resolveInitialPeriod(PolicyContract targetContract, List<PolicyPeriod> policyPeriods) {
        if (targetContract == null || policyPeriods == null) {
            return null;
        }

        return policyPeriods.stream()
                .filter(Objects::nonNull)
                .filter(period -> period.getPolicyContract() != null)
                .filter(period -> Objects.equals(period.getPolicyContract().getId(), targetContract.getId()))
                .sorted((left, right) -> Integer.compare(left.getRenewalSequence() == null ? Integer.MAX_VALUE : left.getRenewalSequence(),
                        right.getRenewalSequence() == null ? Integer.MAX_VALUE : right.getRenewalSequence()))
                .findFirst()
                .orElse(null);
    }

    private static PolicyLegacyIdMap buildMapping(
            PolicyContract targetContract,
            LegacyPolicyMigrationDiscovery.LegacyPolicyRecord legacyPolicy) {

        if (targetContract == null || legacyPolicy == null) {
            return null;
        }

        PolicyLegacyIdMap mapping = new PolicyLegacyIdMap();
        mapping.setLegacySource(legacyPolicy.legacySource());
        mapping.setLegacyPolicyId(legacyPolicy.legacyPolicyId());
        mapping.setPolicyContract(targetContract);
        mapping.setLegacyRecordType(legacyPolicy.legacyRecordType() == null ? "POLICY" : legacyPolicy.legacyRecordType());
        mapping.setCreatedAt(Instant.now());
        mapping.setUpdatedAt(Instant.now());
        return mapping;
    }

    public record LegacyPolicyMigrationPlan(
            LegacyPolicyMigrationDiscovery.LegacyPolicyMigrationDecision decision,
            boolean allowedForAutomaticMigration,
            boolean writesPlanned,
            boolean noOp,
            String action,
            PolicyContract targetContract,
            PolicyPeriod initialPeriod,
            PolicyLegacyIdMap legacyIdMapping,
            String preservedPolicyNumber,
            List<String> auditTrail) {

        public static LegacyPolicyMigrationPlan rejected(
                LegacyPolicyMigrationDiscovery.LegacyPolicyMigrationDecision decision,
                String reason) {
            List<String> audit = new ArrayList<>();
            audit.add(reason);
            return new LegacyPolicyMigrationPlan(
                    decision,
                    false,
                    false,
                    true,
                    "REJECTED_FOR_RECONCILIATION",
                    null,
                    null,
                    null,
                    null,
                    audit);
        }
    }
}
