package com.claimassist.platform.policy_service.migration;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class LegacyPolicyDryRunService {

    public LegacyPolicyDryRunReport dryRun(LegacyPolicyDataset dataset, LegacyPlanMappingResolver mappingResolver) {
        return dryRun(dataset, mappingResolver, null, null);
    }

    public LegacyPolicyDryRunReport dryRun(LegacyPolicyDataset dataset,
                                          LegacyPlanMappingResolver mappingResolver,
                                          TargetPlanLookup targetPlanLookup,
                                          LegacyPolicyReconciliationLookup reconciliationLookup) {
        if (dataset == null || dataset.records() == null || dataset.records().isEmpty()) {
            return new LegacyPolicyDryRunReport(0, 0, 0, 0, 0, 0, List.of(), Instant.now(), "MISSING", false);
        }

        String checksumStatus = dataset.checksumStatus();
        LegacyPolicyDryRunReport report = dryRun(dataset.records(), mappingResolver, targetPlanLookup, reconciliationLookup);
        boolean datasetReady = "VALID".equals(checksumStatus)
                && report.blockedCount() == 0
                && report.duplicateCount() == 0
                && report.unsupportedCount() == 0
                && report.requiresReviewCount() == 0;

        return new LegacyPolicyDryRunReport(
                report.sourceRecordCount(),
                report.safeToMigrateCount(),
                report.requiresReviewCount(),
                report.duplicateCount(),
                report.blockedCount(),
                report.unsupportedCount(),
                report.transformedPolicies(),
                Instant.now(),
                checksumStatus,
                datasetReady);
    }

    public LegacyPolicyDryRunReport dryRun(List<LegacyPolicyRecord> sourcePolicies, LegacyPlanMappingResolver mappingResolver) {
        return dryRun(sourcePolicies, mappingResolver, null, null);
    }

    public LegacyPolicyDryRunReport dryRun(List<LegacyPolicyRecord> sourcePolicies,
                                          LegacyPlanMappingResolver mappingResolver,
                                          TargetPlanLookup targetPlanLookup,
                                          LegacyPolicyReconciliationLookup reconciliationLookup) {
        List<LegacyPolicyTransformationResult> transformed = new ArrayList<>();
        if (sourcePolicies == null || sourcePolicies.isEmpty()) {
            return new LegacyPolicyDryRunReport(0, 0, 0, 0, 0, 0, List.of(), Instant.now(), "NOT_APPLICABLE", false);
        }

        Map<Long, Integer> seenLegacyPolicyIds = new HashMap<>();
        Map<String, Integer> seenPolicyNumbers = new HashMap<>();

        for (LegacyPolicyRecord record : sourcePolicies) {
            if (record != null && record.legacyPolicyId() != null) {
                seenLegacyPolicyIds.merge(record.legacyPolicyId(), 1, Integer::sum);
            }
            if (record != null && record.legacyPolicyNumber() != null && !record.legacyPolicyNumber().isBlank()) {
                seenPolicyNumbers.merge(record.legacyPolicyNumber().trim(), 1, Integer::sum);
            }
        }

        int safeCount = 0;
        int reviewCount = 0;
        int duplicateCount = 0;
        int blockedCount = 0;
        int unsupportedCount = 0;

        for (LegacyPolicyRecord source : sourcePolicies) {
            LegacyPolicyValidationResult validation = validateRecord(
                    source,
                    seenLegacyPolicyIds,
                    seenPolicyNumbers,
                    mappingResolver,
                    targetPlanLookup,
                    reconciliationLookup);
            LegacyPolicyTransformationResult result = transform(source, validation, mappingResolver);
            transformed.add(result);

            switch (validation.classification()) {
                case SAFE_TO_MIGRATE -> safeCount++;
                case REQUIRES_REVIEW -> reviewCount++;
                case DUPLICATE -> duplicateCount++;
                case BLOCKED -> blockedCount++;
                case UNSUPPORTED -> unsupportedCount++;
            }
        }

        return new LegacyPolicyDryRunReport(
                sourcePolicies.size(),
                safeCount,
                reviewCount,
                duplicateCount,
                blockedCount,
                unsupportedCount,
                transformed,
                Instant.now(),
                "NOT_APPLICABLE",
                blockedCount == 0 && duplicateCount == 0 && unsupportedCount == 0 && reviewCount == 0);
    }

    /**
     * Public validation method for single record validation.
     * Used by execution service to maintain consistency with dry-run logic.
     */
    public LegacyPolicyValidationResult validateRecordPublic(
            LegacyPolicyRecord source,
            LegacyPlanMappingResolver mappingResolver,
            TargetPlanLookup targetPlanLookup,
            LegacyPolicyReconciliationLookup reconciliationLookup) {

        // Build seen maps for single record validation
        Map<Long, Integer> seenLegacyPolicyIds = new HashMap<>();
        Map<String, Integer> seenPolicyNumbers = new HashMap<>();

        if (source != null && source.legacyPolicyId() != null) {
            seenLegacyPolicyIds.put(source.legacyPolicyId(), 1);
        }
        if (source != null && source.legacyPolicyNumber() != null && !source.legacyPolicyNumber().isBlank()) {
            seenPolicyNumbers.put(source.legacyPolicyNumber().trim(), 1);
        }

        return validateRecord(source, seenLegacyPolicyIds, seenPolicyNumbers, mappingResolver, targetPlanLookup, reconciliationLookup);
    }

    private LegacyPolicyValidationResult validateRecord(
            LegacyPolicyRecord source,
            Map<Long, Integer> seenLegacyPolicyIds,
            Map<String, Integer> seenPolicyNumbers,
            LegacyPlanMappingResolver mappingResolver,
            TargetPlanLookup targetPlanLookup,
            LegacyPolicyReconciliationLookup reconciliationLookup
    ) {
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (source == null) {
            return new LegacyPolicyValidationResult(MigrationClassification.BLOCKED, List.of("Source record is null"), List.of(), null, null, "UNSET", "NONE");
        }

        if (source.legacyPolicyId() == null) {
            reasons.add("INVALID_CUSTOMER_ID");
        }
        if (source.legacyCustomerId() == null) {
            reasons.add("INVALID_CUSTOMER_ID");
        }
        if (source.legacyPolicyNumber() == null || source.legacyPolicyNumber().isBlank()) {
            reasons.add("POLICY_NUMBER_CONFLICT");
        }
        if (source.legacyCoveragePlanId() == null) {
            reasons.add("MISSING_PLAN_MAPPING");
        }
        if (source.effectiveDate() == null) {
            reasons.add("INVALID_DATE_RANGE");
        }

        if (source.legacyPolicyId() != null && seenLegacyPolicyIds.getOrDefault(source.legacyPolicyId(), 0) > 1) {
            reasons.add("LEGACY_ID_CONFLICT");
        }
        if (source.legacyPolicyNumber() != null && !source.legacyPolicyNumber().isBlank() && seenPolicyNumbers.getOrDefault(source.legacyPolicyNumber().trim(), 0) > 1) {
            reasons.add("POLICY_NUMBER_CONFLICT");
        }

        if (source.renewalDate() != null && source.effectiveDate() != null && source.renewalDate().isBefore(source.effectiveDate())) {
            reasons.add("INVALID_DATE_RANGE");
        }

        Optional<Long> mappedTargetPlanId = mappingResolver == null ? Optional.empty() : mappingResolver.resolveTargetPlanId(source);
        Long targetPlanId = mappedTargetPlanId.orElse(null);

        if (source.legacyCoveragePlanId() != null && targetPlanId == null) {
            reasons.add("MISSING_PLAN_MAPPING");
        }

        if (targetPlanId != null && targetPlanLookup != null) {
            TargetPlanState planState = targetPlanLookup.findById(targetPlanId).orElse(TargetPlanState.missing(targetPlanId));
            if (!planState.exists()) {
                reasons.add("TARGET_PLAN_NOT_FOUND");
            }
            if (planState.exists() && !planState.active()) {
                reasons.add("TARGET_PLAN_INACTIVE");
            }
        }

        if (reconciliationLookup != null && source.legacyPolicyId() != null) {
            Optional<LegacyPolicyReconciliation> byLegacy = reconciliationLookup.findByLegacyPolicyId(source.legacyPolicyId());
            if (byLegacy.isPresent()) {
                Long existingTargetId = byLegacy.get().targetPolicyId();
                if (targetPlanId != null && !Objects.equals(existingTargetId, targetPlanId)) {
                    reasons.add("LEGACY_ID_CONFLICT");
                }
                if (targetPlanId == null && existingTargetId != null) {
                    reasons.add("RECONCILIATION_CONFLICT");
                }
            }

            if (targetPlanId != null) {
                Optional<LegacyPolicyReconciliation> byTarget = reconciliationLookup.findByTargetPolicyId(targetPlanId);
                if (byTarget.isPresent() && !Objects.equals(byTarget.get().legacyPolicyId(), source.legacyPolicyId())) {
                    reasons.add("RECONCILIATION_CONFLICT");
                }
            }
        }

        if (source.legacyPolicyNumber() != null && !source.legacyPolicyNumber().isBlank() && reconciliationLookup != null) {
            String normalizedPolicyNumber = source.legacyPolicyNumber().trim();
            Optional<LegacyPolicyReconciliation> byPolicyNumber = reconciliationLookup.findByLegacyPolicyNumber(normalizedPolicyNumber);
            if (byPolicyNumber.isPresent()) {
                Long mappedTargetId = byPolicyNumber.get().targetPolicyId();
                if (targetPlanId != null && !Objects.equals(mappedTargetId, targetPlanId)) {
                    reasons.add("POLICY_NUMBER_CONFLICT");
                }
                if (targetPlanId == null && mappedTargetId != null) {
                    reasons.add("POLICY_NUMBER_CONFLICT");
                }
            }
        }

        if (source.productType() == null || source.productType().isBlank()) {
            warnings.add("Legacy product type is blank; target plan mapping is still required before migration");
        }

        String normalizedStatus = source.normalizedStatus();
        if ("PENDING".equals(normalizedStatus)
                || "LAPSED".equals(normalizedStatus)
                || "PENDING_RENEWAL".equals(normalizedStatus)) {
            reasons.add("Legacy status requires business approval before migration: " + normalizedStatus);
        }

        String mappedStatus = switch (normalizedStatus) {
            case "ACTIVE" -> "ACTIVE";
            case "CANCELLED" -> "CANCELLED";
            case "PENDING" -> "UNRESOLVED";
            case "LAPSED" -> "UNRESOLVED";
            case "PENDING_RENEWAL" -> "UNRESOLVED";
            default -> "UNSUPPORTED";
        };

        String premiumDecision = source.annualPremiumCents() == null
                ? "PREMIUM_REQUIRES_APPROVAL"
                : "PROTECTED_FROM_DEDUCTIBLE_DERIVATION";

        String stripeDecision = (source.stripePriceId() != null || source.stripeSubscriptionId() != null)
                ? "AUDIT_ONLY_NO_TARGET_FIELD"
                : "NONE";

        if (source.annualPremiumCents() != null && source.deductibleCents() != null && source.annualPremiumCents().equals(source.deductibleCents())) {
            warnings.add("Annual premium matches deductible; migration must still not derive premium from deductible without approval");
        }

        boolean duplicateLegacyPolicyId = source.legacyPolicyId() != null
                && seenLegacyPolicyIds.getOrDefault(source.legacyPolicyId(), 0) > 1;
        boolean duplicatePolicyNumber = source.legacyPolicyNumber() != null
                && !source.legacyPolicyNumber().isBlank()
                && seenPolicyNumbers.getOrDefault(source.legacyPolicyNumber().trim(), 0) > 1;

        if (duplicateLegacyPolicyId || duplicatePolicyNumber) {
            return new LegacyPolicyValidationResult(MigrationClassification.DUPLICATE, reasons, warnings, targetPlanId, mappedStatus, premiumDecision, stripeDecision);
        }

        if (!reasons.isEmpty()) {
            boolean blockingDataIssue = source.legacyPolicyId() == null
                    || source.legacyCustomerId() == null
                    || source.legacyPolicyNumber() == null || source.legacyPolicyNumber().isBlank()
                    || source.legacyCoveragePlanId() == null
                    || source.effectiveDate() == null
                    || (source.renewalDate() != null && source.effectiveDate() != null && source.renewalDate().isBefore(source.effectiveDate()))
                    || reasons.stream().anyMatch(reason -> reason.contains("TARGET_PLAN_NOT_FOUND")
                            || reason.contains("TARGET_PLAN_INACTIVE")
                            || reason.contains("LEGACY_ID_CONFLICT")
                            || reason.contains("RECONCILIATION_CONFLICT")
                            || reason.contains("POLICY_NUMBER_CONFLICT")
                            || reason.contains("INVALID_CUSTOMER_ID")
                            || reason.contains("INVALID_DATE_RANGE"));
            if (blockingDataIssue) {
                return new LegacyPolicyValidationResult(MigrationClassification.BLOCKED, reasons, warnings, targetPlanId, mappedStatus, premiumDecision, stripeDecision);
            }
            return new LegacyPolicyValidationResult(MigrationClassification.REQUIRES_REVIEW, reasons, warnings, targetPlanId, mappedStatus, premiumDecision, stripeDecision);
        }

        if (targetPlanId == null) {
            return new LegacyPolicyValidationResult(MigrationClassification.REQUIRES_REVIEW,
                    List.of("MISSING_PLAN_MAPPING"),
                    warnings,
                    null,
                    mappedStatus,
                    premiumDecision,
                    stripeDecision);
        }

        switch (normalizedStatus) {
            case "ACTIVE", "CANCELLED" -> {
                if (source.annualPremiumCents() == null) {
                    return new LegacyPolicyValidationResult(MigrationClassification.REQUIRES_REVIEW,
                            List.of("PREMIUM_REQUIRES_APPROVAL"),
                            warnings,
                            targetPlanId,
                            mappedStatus,
                            premiumDecision,
                            stripeDecision);
                }
                return new LegacyPolicyValidationResult(MigrationClassification.SAFE_TO_MIGRATE,
                        List.of(),
                        warnings,
                        targetPlanId,
                        mappedStatus,
                        premiumDecision,
                        stripeDecision);
            }
            case "PENDING", "LAPSED", "PENDING_RENEWAL" -> {
                return new LegacyPolicyValidationResult(MigrationClassification.REQUIRES_REVIEW,
                        List.of("Legacy status requires business approval before migration: " + normalizedStatus),
                        warnings,
                        targetPlanId,
                        mappedStatus,
                        premiumDecision,
                        stripeDecision);
            }
            default -> {
                return new LegacyPolicyValidationResult(MigrationClassification.UNSUPPORTED,
                        List.of("UNSUPPORTED_STATUS"),
                        warnings,
                        targetPlanId,
                        mappedStatus,
                        premiumDecision,
                        stripeDecision);
            }
        }
    }

    private LegacyPolicyTransformationResult transform(
            LegacyPolicyRecord source,
            LegacyPolicyValidationResult validation,
            LegacyPlanMappingResolver mappingResolver
    ) {
        Long targetPlanId = validation.targetPlanId();
        if (targetPlanId == null && mappingResolver != null) {
            targetPlanId = mappingResolver.resolveTargetPlanId(source).orElse(null);
        }

        String targetStatus = "UNRESOLVED";
        if (validation.targetStatus() != null) {
            targetStatus = validation.targetStatus();
        }

        String coverageDecision = "AGGREGATE_ONLY_NOT_DECOMPOSED";
        String stripeDecision = validation.stripeDecision() == null ? "NONE" : validation.stripeDecision();
        String premiumDecision = validation.premiumDecision() == null ? "PREMIUM_REQUIRES_APPROVAL" : validation.premiumDecision();

        return new LegacyPolicyTransformationResult(
                source == null ? null : source.legacyPolicyId(),
                source == null ? null : source.legacyCustomerId(),
                source == null ? null : source.legacyPolicyNumber(),
                targetPlanId,
                null,
                targetStatus,
                source == null ? null : source.effectiveDate(),
                source == null ? null : source.renewalDate(),
                "TARGET_RECORD_CREATION_TIME",
                premiumDecision,
                coverageDecision,
                stripeDecision,
                validation.classification(),
                validation.reasons()
        );
    }
}
