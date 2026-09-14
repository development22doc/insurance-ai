package com.claimassist.platform.policy_service.migration;

import com.claimassist.platform.policy_service.entity.PolicyContract;
import com.claimassist.platform.policy_service.entity.PolicyLegacyIdMap;
import com.claimassist.platform.policy_service.entity.PolicyPeriod;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class LegacyPolicyMigrationDiscovery {

    public static final String CUSTOMER_SERVICE_SOURCE = "customer-service";
    public static final String POLICY_SERVICE_SOURCE = "policy-service";
    public static final String POLICY_RECORD_TYPE = "POLICY";

    private LegacyPolicyMigrationDiscovery() {
        // read-only discovery utility, no instance needed
    }

    public static LegacyPolicyMigrationDecision classify(
            LegacyPolicyRecord legacyPolicy,
            List<PolicyContract> policyContracts,
            List<PolicyLegacyIdMap> existingMappings,
            List<PolicyPeriod> policyPeriods) {

        Objects.requireNonNull(legacyPolicy, "legacyPolicy cannot be null");
        List<PolicyContract> contracts = policyContracts == null ? List.of() : policyContracts;
        List<PolicyLegacyIdMap> mappings = existingMappings == null ? List.of() : existingMappings;
        List<PolicyPeriod> periods = policyPeriods == null ? List.of() : policyPeriods;

        if (legacyPolicy.legacySource() == null || legacyPolicy.legacySource().isBlank()) {
            return decision(LegacyPolicyClassification.INVALID, legacyPolicy,
                    null, List.of(), "Claim policy source is unknown; fail closed until the source is explicitly qualified.");
        }

        if (legacyPolicy.legacyPolicyId() == null) {
            return decision(LegacyPolicyClassification.INVALID, legacyPolicy,
                    null, List.of(), "Legacy policy ID is required for deterministic source-qualified identity.");
        }

        if (legacyPolicy.effectiveDate() == null || legacyPolicy.expirationDate() == null
                || !legacyPolicy.effectiveDate().isBefore(legacyPolicy.expirationDate())) {
            return decision(LegacyPolicyClassification.INVALID, legacyPolicy,
                    null, List.of(), "Legacy dates are invalid: effectiveDate must be before expirationDate.");
        }

        List<PolicyLegacyIdMap> matchingMappings = mappings.stream()
                .filter(mapping -> Objects.equals(mapping.getLegacySource(), legacyPolicy.legacySource()))
                .filter(mapping -> Objects.equals(mapping.getLegacyPolicyId(), legacyPolicy.legacyPolicyId()))
                .toList();

        if (matchingMappings.size() > 1) {
            return decision(LegacyPolicyClassification.AMBIGUOUS, legacyPolicy,
                    null, matchingMappings.stream().map(map -> map.getPolicyContract().getId()).toList(),
                    "Duplicate legacy-source mapping rows exist for the same source-qualified identity.");
        }

        if (!matchingMappings.isEmpty()) {
            PolicyLegacyIdMap mapping = matchingMappings.getFirst();
            return decision(LegacyPolicyClassification.ALREADY_MAPPED, legacyPolicy,
                    mapping.getPolicyContract().getId(), List.of(mapping.getPolicyContract().getId()),
                    "Deterministic mapping already exists; no new contract should be created.");
        }

        List<PolicyContract> exactCustomerNumberMatches = contracts.stream()
                .filter(contract -> Objects.equals(contract.getCustomerId(), legacyPolicy.customerId()))
                .filter(contract -> Objects.equals(contract.getPolicyNumber(), legacyPolicy.policyNumber()))
                .toList();

        if (exactCustomerNumberMatches.size() > 1) {
            return decision(LegacyPolicyClassification.AMBIGUOUS, legacyPolicy,
                    null, exactCustomerNumberMatches.stream().map(PolicyContract::getId).toList(),
                    "Multiple authoritative PolicyContracts match the same customer and policy number.");
        }

        if (exactCustomerNumberMatches.size() == 1) {
            PolicyContract contract = exactCustomerNumberMatches.getFirst();
            if (contract.getProductId() == null || contract.getProductId() <= 0) {
                return decision(LegacyPolicyClassification.INVALID, legacyPolicy,
                        contract.getId(), List.of(contract.getId()),
                        "Contract is structurally incomplete: productId is missing.");
            }

            if (!isContractCompatibleWithLegacy(contract, legacyPolicy, periods)) {
                return decision(LegacyPolicyClassification.INVALID, legacyPolicy,
                        contract.getId(), List.of(contract.getId()),
                        "The matching contract contradicts the legacy record: product/plan/customer/date invariants do not align.");
            }

            return decision(LegacyPolicyClassification.SAFE_MATCH, legacyPolicy,
                    contract.getId(), List.of(contract.getId()),
                    "Exact customer + policy number match with valid contract invariants and source-qualified identity is deterministic.");
        }

        List<PolicyContract> samePolicyNumberContracts = contracts.stream()
                .filter(contract -> Objects.equals(contract.getPolicyNumber(), legacyPolicy.policyNumber()))
                .toList();

        if (!samePolicyNumberContracts.isEmpty()) {
            return decision(LegacyPolicyClassification.UNMATCHED, legacyPolicy,
                    null, samePolicyNumberContracts.stream().map(PolicyContract::getId).toList(),
                    "The same policy number exists in authoritative data but customer/source identity does not match deterministically; no fuzzy matching allowed.");
        }

        List<PolicyContract> sameCustomerContracts = contracts.stream()
                .filter(contract -> Objects.equals(contract.getCustomerId(), legacyPolicy.customerId()))
                .toList();

        if (!sameCustomerContracts.isEmpty()) {
            return decision(LegacyPolicyClassification.UNMATCHED, legacyPolicy,
                    null, sameCustomerContracts.stream().map(PolicyContract::getId).toList(),
                    "Customer matches but the legacy record does not deterministically match an authoritative contract; no fuzzy matching allowed.");
        }

        return decision(LegacyPolicyClassification.CUSTOMER_SERVICE_ONLY, legacyPolicy,
                null, List.of(),
                "Legacy record exists in Customer Service without a matching Policy Service contract; it requires reconciliation and cannot be auto-migrated.");
    }

    public static List<LegacyPolicyMigrationDecision> dryRun(
            List<LegacyPolicyRecord> legacyPolicies,
            List<PolicyContract> policyContracts,
            List<PolicyLegacyIdMap> existingMappings,
            List<PolicyPeriod> policyPeriods) {

        if (legacyPolicies == null || legacyPolicies.isEmpty()) {
            return List.of();
        }

        return legacyPolicies.stream()
                .sorted(Comparator.comparing(LegacyPolicyRecord::legacySource, Comparator.nullsFirst(String::compareTo))
                        .thenComparing(LegacyPolicyRecord::legacyPolicyId, Comparator.nullsFirst(Long::compareTo))
                        .thenComparing(LegacyPolicyRecord::customerId, Comparator.nullsFirst(Long::compareTo)))
                .map(record -> classify(record, policyContracts, existingMappings, policyPeriods))
                .collect(Collectors.toList());
    }

    public static boolean validateContractAndPeriodInvariants(
            PolicyContract contract,
            List<PolicyPeriod> periods,
            List<PolicyContract> allContracts) {

        if (contract == null) {
            return false;
        }
        if (contract.getCustomerId() == null || contract.getProductId() == null || contract.getPolicyNumber() == null
                || contract.getPolicyNumber().isBlank() || contract.getStatus() == null || contract.getStatus().isBlank()) {
            return false;
        }

        long duplicatePolicyNumbers = allContracts == null ? 0 : allContracts.stream()
                .filter(other -> other != null && Objects.equals(other.getPolicyNumber(), contract.getPolicyNumber()))
                .count();
        if (duplicatePolicyNumbers > 1) {
            return false;
        }

        if (periods == null) {
            return true;
        }

        List<PolicyPeriod> contractPeriods = periods.stream()
                .filter(period -> period != null && period.getPolicyContract() != null && Objects.equals(period.getPolicyContract().getId(), contract.getId()))
                .toList();

        for (PolicyPeriod period : contractPeriods) {
            if (period.getEffectiveDate() == null || period.getExpirationDate() == null)
                return false;
            if (!period.getEffectiveDate().isBefore(period.getExpirationDate()))
                return false;
            if (period.getPlanId() == null || period.getPlanId() <= 0)
                return false;
        }

        for (int i = 0; i < contractPeriods.size(); i++) {
            PolicyPeriod left = contractPeriods.get(i);
            for (int j = i + 1; j < contractPeriods.size(); j++) {
                PolicyPeriod right = contractPeriods.get(j);
                if (left.getEffectiveDate().isBefore(right.getExpirationDate()) && right.getEffectiveDate().isBefore(left.getExpirationDate())) {
                    return false;
                }
            }
        }

        return true;
    }

    public static LegacyPolicyMigrationDecision classifyClaimPolicyId(
            Long legacyPolicyId,
            String sourceHint,
            List<PolicyContract> policyContracts,
            List<PolicyLegacyIdMap> existingMappings,
            List<PolicyPeriod> policyPeriods) {

        LegacyPolicyRecord record = new LegacyPolicyRecord(
                sourceHint,
                legacyPolicyId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                POLICY_RECORD_TYPE);

        return classify(record, policyContracts, existingMappings, policyPeriods);
    }

    private static LegacyPolicyMigrationDecision decision(
            LegacyPolicyClassification classification,
            LegacyPolicyRecord legacyPolicy,
            Long targetPolicyContractId,
            List<Long> candidatePolicyContractIds,
            String reason) {

        List<Long> safeCandidates = candidatePolicyContractIds == null ? Collections.emptyList() : candidatePolicyContractIds;
        return new LegacyPolicyMigrationDecision(
                classification,
                legacyPolicy.legacySource(),
                legacyPolicy.legacyPolicyId(),
                targetPolicyContractId,
                safeCandidates,
                reason,
                "source-qualified-identity" + (legacyPolicy.customerId() == null ? "" : ",customer=" + legacyPolicy.customerId()),
                classification != LegacyPolicyClassification.SAFE_MATCH && classification != LegacyPolicyClassification.ALREADY_MAPPED);
    }

    private static boolean isContractCompatibleWithLegacy(
            PolicyContract contract,
            LegacyPolicyRecord legacyPolicy,
            List<PolicyPeriod> periods) {

        if (contract == null) {
            return false;
        }

        if (!Objects.equals(contract.getCustomerId(), legacyPolicy.customerId())) {
            return false;
        }

        if (legacyPolicy.policyNumber() != null && !legacyPolicy.policyNumber().equals(contract.getPolicyNumber())) {
            return false;
        }

        if (legacyPolicy.effectiveDate() != null && legacyPolicy.expirationDate() != null) {
            boolean dateCompatible = false;
            List<PolicyPeriod> contractPeriods = periods.stream()
                    .filter(period -> period.getPolicyContract() != null && Objects.equals(period.getPolicyContract().getId(), contract.getId()))
                    .toList();

            for (PolicyPeriod period : contractPeriods) {
                boolean matchesPeriodRange = !period.getEffectiveDate().isAfter(legacyPolicy.effectiveDate())
                        && !legacyPolicy.expirationDate().isAfter(period.getExpirationDate())
                        && period.getEffectiveDate().isBefore(period.getExpirationDate());
                if (matchesPeriodRange) {
                    dateCompatible = true;
                    break;
                }
            }
            if (!dateCompatible && !contractPeriods.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public enum LegacyPolicyClassification {
        SAFE_MATCH,
        AMBIGUOUS,
        UNMATCHED,
        INVALID,
        CUSTOMER_SERVICE_ONLY,
        ALREADY_MAPPED
    }

    public record LegacyPolicyRecord(
            String legacySource,
            Long legacyPolicyId,
            Long customerId,
            String policyNumber,
            String productCode,
            String planCode,
            Instant effectiveDate,
            Instant expirationDate,
            String status,
            String legacyRecordType) {
    }

    public record LegacyPolicyMigrationDecision(
            LegacyPolicyClassification classification,
            String legacySource,
            Long legacyPolicyId,
            Long targetPolicyContractId,
            List<Long> candidatePolicyContractIds,
            String reason,
            String rule,
            boolean requiresManualAction) {
    }
}
