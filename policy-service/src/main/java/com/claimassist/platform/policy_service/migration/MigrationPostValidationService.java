package com.claimassist.platform.policy_service.migration;

import com.claimassist.platform.policy_service.entity.Policy;
import com.claimassist.platform.policy_service.entity.PolicyVersion;
import com.claimassist.platform.policy_service.repository.LegacyCustomerPolicyIdMapRepository;
import com.claimassist.platform.policy_service.repository.PolicyRepository;
import com.claimassist.platform.policy_service.repository.PolicyVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MigrationPostValidationService {

    private final LegacyCustomerPolicyIdMapRepository reconciliationRepository;
    private final PolicyRepository policyRepository;
    private final PolicyVersionRepository policyVersionRepository;

    /**
     * Validate migration results by comparing source, target, and reconciliation data.
     */
    public MigrationPostValidationResult validateMigration(
            LegacyPolicyDataset sourceDataset,
            LegacyPolicyExecutionResult executionResult) {

        List<MigrationPostValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Validate counts
        int sourceCount = sourceDataset != null ? sourceDataset.recordCount() : 0;
        int targetCount = (int) policyRepository.count();
        int reconciliationCount = (int) reconciliationRepository.count();
        int migratedCount = executionResult != null ? executionResult.successfullyMigratedCount() : 0;

        if (sourceCount != reconciliationCount + executionResult.alreadyMigratedCount()) {
            issues.add(new MigrationPostValidationResult.ValidationIssue(
                    "COUNT_MISMATCH",
                    "Source count does not match reconciliation count + already migrated count",
                    String.format("Source: %d, Reconciliation: %d, Already Migrated: %d",
                            sourceCount, reconciliationCount, executionResult.alreadyMigratedCount())
            ));
        }

        if (migratedCount != reconciliationCount) {
            issues.add(new MigrationPostValidationResult.ValidationIssue(
                    "MIGRATION_COUNT_MISMATCH",
                    "Successfully migrated count does not match reconciliation count",
                    String.format("Migrated: %d, Reconciliation: %d", migratedCount, reconciliationCount)
            ));
        }

        // Validate each migrated policy has PolicyVersion
        if (executionResult != null && executionResult.recordResults() != null) {
            for (MigrationRecordResult result : executionResult.recordResults()) {
                if (result.status() == MigrationRecordResult.Status.SUCCESSFULLY_MIGRATED && result.targetPolicyId() != null) {
                    validatePolicyHasVersion(result.targetPolicyId(), issues);
                    if (result.legacyCustomerId() != null) {
                        validatePolicyOwnership(result.legacyCustomerId(), result.targetPolicyId(), issues);
                    }
                }
            }
        }

        // Validate policy number uniqueness
        validatePolicyNumberUniqueness(issues);

        // Validate checksum consistency if available
        if (sourceDataset != null && sourceDataset.hasChecksum()) {
            if (!sourceDataset.isChecksumValid()) {
                issues.add(new MigrationPostValidationResult.ValidationIssue(
                        "CHECKSUM_INVALID",
                        "Source dataset checksum is invalid",
                        "Source data may have changed since extraction"
                ));
            }
        }

        boolean overallValid = issues.isEmpty();
        return new MigrationPostValidationResult(
                overallValid,
                sourceCount,
                targetCount,
                migratedCount,
                reconciliationCount,
                issues,
                Instant.now()
        );
    }

    private void validatePolicyHasVersion(Long policyId, List<MigrationPostValidationResult.ValidationIssue> issues) {
        List<PolicyVersion> versions = policyVersionRepository.findByPolicyIdOrderByVersionNumber(policyId);
        if (versions == null || versions.isEmpty()) {
            issues.add(new MigrationPostValidationResult.ValidationIssue(
                    "MISSING_POLICY_VERSION",
                    "Migrated policy has no PolicyVersion",
                    "Policy ID: " + policyId
            ));
        } else if (versions.get(0).getVersionNumber() != 1) {
            issues.add(new MigrationPostValidationResult.ValidationIssue(
                    "INCORRECT_VERSION_NUMBER",
                    "Initial PolicyVersion is not version 1",
                    "Policy ID: " + policyId + ", Version: " + versions.get(0).getVersionNumber()
            ));
        }
    }

    private void validatePolicyOwnership(Long expectedCustomerId, Long policyId, List<MigrationPostValidationResult.ValidationIssue> issues) {
        if (expectedCustomerId == null) {
            return; // Skip if no customer ID available
        }

        policyRepository.findById(policyId).ifPresent(policy -> {
            if (!expectedCustomerId.equals(policy.getCustomerId())) {
                issues.add(new MigrationPostValidationResult.ValidationIssue(
                        "CUSTOMER_OWNERSHIP_MISMATCH",
                        "Policy customer ID does not match expected customer ID",
                        String.format("Policy ID: %d, Expected: %d, Actual: %d",
                                policyId, expectedCustomerId, policy.getCustomerId())
                ));
            }
        });
    }

    private void validatePolicyNumberUniqueness(List<MigrationPostValidationResult.ValidationIssue> issues) {
        // Check for duplicate policy numbers in target
        List<Policy> allPolicies = new ArrayList<>();
        policyRepository.findAll().forEach(allPolicies::add);
        Map<String, Long> policyNumberCount = allPolicies.stream()
                .collect(Collectors.groupingBy(Policy::getPolicyNumber, Collectors.counting()));

        policyNumberCount.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .forEach(entry -> {
                    issues.add(new MigrationPostValidationResult.ValidationIssue(
                            "DUPLICATE_POLICY_NUMBER",
                            "Policy number is not unique in target",
                            "Policy Number: " + entry.getKey() + ", Count: " + entry.getValue()
                    ));
                });
    }
}
