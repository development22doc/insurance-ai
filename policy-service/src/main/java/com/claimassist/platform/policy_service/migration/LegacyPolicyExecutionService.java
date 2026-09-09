package com.claimassist.platform.policy_service.migration;

import com.claimassist.platform.policy_service.entity.LegacyCustomerPolicyIdMap;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.Policy;
import com.claimassist.platform.policy_service.entity.PolicyVersion;
import com.claimassist.platform.policy_service.repository.LegacyCustomerPolicyIdMapRepository;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.PolicyRepository;
import com.claimassist.platform.policy_service.repository.PolicyVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyPolicyExecutionService {

    private final LegacyPolicyDryRunService dryRunService;
    private final LegacyPlanMappingResolver mappingResolver;
    private final LegacyPolicyReconciliationLookup reconciliationLookup;
    private final TargetPlanLookup targetPlanLookup;
    private final PolicyRepository policyRepository;
    private final PolicyVersionRepository policyVersionRepository;
    private final LegacyCustomerPolicyIdMapRepository reconciliationRepository;
    private final PlanRepository planRepository;

    /**
     * Execute migration for a single legacy policy record.
     * This is transactional - Policy + PolicyVersion + Reconciliation must all succeed or fail together.
     */
    @Transactional
    public MigrationRecordResult migrateSinglePolicy(LegacyPolicyRecord legacyRecord) {
        if (legacyRecord == null || legacyRecord.legacyPolicyId() == null) {
            return new MigrationRecordResult(
                    null,
                    null,
                    MigrationRecordResult.Status.FAILED,
                    List.of("Invalid legacy record"),
                    "Record is null or missing legacy policy ID",
                    null
            );
        }

        // Check if already migrated
        Optional<LegacyPolicyReconciliation> existing = reconciliationLookup.findByLegacyPolicyId(legacyRecord.legacyPolicyId());
        if (existing.isPresent()) {
            // Verify source hasn't changed (simple check via policy number)
            if (existing.get().legacyPolicyNumber() != null &&
                existing.get().legacyPolicyNumber().equals(legacyRecord.legacyPolicyNumber())) {
                return new MigrationRecordResult(
                        legacyRecord.legacyPolicyId(),
                        existing.get().targetPolicyId(),
                        MigrationRecordResult.Status.ALREADY_MIGRATED,
                        List.of("Policy already migrated"),
                        null,
                        legacyRecord.legacyCustomerId()
                );
            } else {
                return new MigrationRecordResult(
                        legacyRecord.legacyPolicyId(),
                        existing.get().targetPolicyId(),
                        MigrationRecordResult.Status.SKIPPED,
                        List.of("SOURCE_CHANGED"),
                        "Legacy policy number changed since migration",
                        legacyRecord.legacyCustomerId()
                );
            }
        }

        // Validate using dry-run logic
        LegacyPolicyValidationResult validation = dryRunService.validateRecordPublic(
                legacyRecord,
                mappingResolver,
                targetPlanLookup,
                reconciliationLookup
        );

        if (!validation.isSafeToMigrate()) {
            return new MigrationRecordResult(
                    legacyRecord.legacyPolicyId(),
                    null,
                    MigrationRecordResult.Status.SKIPPED,
                    validation.reasons(),
                    validation.classification().name(),
                    legacyRecord.legacyCustomerId()
            );
        }

        // Execute migration
        try {
            return createPolicyWithVersionAndReconciliation(legacyRecord, validation);
        } catch (Exception e) {
            log.error("Migration failed for legacy policy ID {}: {}", legacyRecord.legacyPolicyId(), e.getMessage(), e);
            return new MigrationRecordResult(
                    legacyRecord.legacyPolicyId(),
                    null,
                    MigrationRecordResult.Status.FAILED,
                    List.of("Migration execution failed"),
                    e.getMessage(),
                    legacyRecord.legacyCustomerId()
            );
        }
    }

    /**
     * Execute migration for an entire dataset.
     * Processes records one by one to maintain transaction isolation per record.
     */
    public LegacyPolicyExecutionResult migrateDataset(LegacyPolicyDataset dataset) {
        if (dataset == null || dataset.records() == null || dataset.records().isEmpty()) {
            return new LegacyPolicyExecutionResult(
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    Instant.now(),
                    "EMPTY_DATASET"
            );
        }

        // Verify checksum if present
        if (dataset.hasChecksum() && !dataset.isChecksumValid()) {
            return new LegacyPolicyExecutionResult(
                    dataset.recordCount(),
                    0,
                    0,
                    0,
                    dataset.recordCount(),
                    List.of(),
                    Instant.now(),
                    "CHECKSUM_INVALID"
            );
        }

        List<MigrationRecordResult> recordResults = new ArrayList<>();
        int alreadyMigratedCount = 0;
        int successfullyMigratedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (LegacyPolicyRecord record : dataset.records()) {
            MigrationRecordResult result = migrateSinglePolicy(record);
            recordResults.add(result);

            switch (result.status()) {
                case ALREADY_MIGRATED -> alreadyMigratedCount++;
                case SUCCESSFULLY_MIGRATED -> successfullyMigratedCount++;
                case SKIPPED -> skippedCount++;
                case FAILED -> failedCount++;
            }
        }

        String executionStatus = failedCount == 0 ? "SUCCESS" : "PARTIAL_FAILURE";
        if (successfullyMigratedCount == 0 && alreadyMigratedCount == 0) {
            executionStatus = "NO_MIGRATION";
        }

        return new LegacyPolicyExecutionResult(
                dataset.recordCount(),
                alreadyMigratedCount,
                successfullyMigratedCount,
                skippedCount,
                failedCount,
                recordResults,
                Instant.now(),
                executionStatus
        );
    }

    /**
     * Create Policy, PolicyVersion, and Reconciliation mapping in a single transaction.
     * This method is called within a @Transactional context.
     */
    private MigrationRecordResult createPolicyWithVersionAndReconciliation(
            LegacyPolicyRecord legacyRecord,
            LegacyPolicyValidationResult validation) {

        Long targetPlanId = validation.targetPlanId();
        if (targetPlanId == null) {
            return new MigrationRecordResult(
                    legacyRecord.legacyPolicyId(),
                    null,
                    MigrationRecordResult.Status.FAILED,
                    List.of("Missing target plan ID"),
                    null,
                    legacyRecord.legacyCustomerId()
            );
        }

        // Fetch target plan with authoritative premium
        Optional<Plan> planOpt = planRepository.findById(targetPlanId);
        if (planOpt.isEmpty() || planOpt.get().getPremiumCents() == null) {
            return new MigrationRecordResult(
                    legacyRecord.legacyPolicyId(),
                    null,
                    MigrationRecordResult.Status.FAILED,
                    List.of("Target plan not found or missing premium"),
                    null,
                    legacyRecord.legacyCustomerId()
            );
        }

        Plan plan = planOpt.get();

        // Create Policy
        Policy policy = Policy.builder()
                .policyNumber(legacyRecord.legacyPolicyNumber())
                .customerId(legacyRecord.legacyCustomerId())
                .coveragePlan(plan)
                .status(validation.targetStatus())
                .effectiveDate(legacyRecord.effectiveDate())
                .renewalDate(legacyRecord.renewalDate())
                .build();

        Policy savedPolicy = policyRepository.save(policy);

        // Create initial PolicyVersion (version 1)
        PolicyVersion policyVersion = PolicyVersion.builder()
                .policy(savedPolicy)
                .versionNumber(1)
                .plan(plan)
                .premiumCents(plan.getPremiumCents()) // Use authoritative Plan premium
                .deductibleCents(plan.getDeductibleCents())
                .coverageLimitCents(plan.getCoverageLimitCents())
                .effectiveFrom(legacyRecord.effectiveDate())
                .effectiveTo(legacyRecord.renewalDate())
                .build();

        policyVersionRepository.save(policyVersion);

        // Create reconciliation mapping
        LegacyCustomerPolicyIdMap reconciliation = LegacyCustomerPolicyIdMap.builder()
                .legacyCustomerPolicyId(legacyRecord.legacyPolicyId())
                .policyServicePolicyId(savedPolicy.getId())
                .sourceSystem(legacyRecord.sourceSystem())
                .legacyCustomerId(legacyRecord.legacyCustomerId())
                .legacyPolicyNumber(legacyRecord.legacyPolicyNumber())
                .build();

        reconciliationRepository.save(reconciliation);

        return new MigrationRecordResult(
                legacyRecord.legacyPolicyId(),
                savedPolicy.getId(),
                MigrationRecordResult.Status.SUCCESSFULLY_MIGRATED,
                List.of(),
                null,
                legacyRecord.legacyCustomerId()
        );
    }
}
