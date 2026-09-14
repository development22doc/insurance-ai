package com.claimassist.platform.policy_service.migration;

import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.PolicyContract;
import com.claimassist.platform.policy_service.entity.PolicyLegacyIdMap;
import com.claimassist.platform.policy_service.entity.PolicyPeriod;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.PolicyContractRepository;
import com.claimassist.platform.policy_service.repository.PolicyLegacyIdMapRepository;
import com.claimassist.platform.policy_service.repository.PolicyPeriodRepository;
import com.claimassist.platform.policy_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyPolicyMigrationExecutor {

    private final PolicyContractRepository policyContractRepository;
    private final PolicyPeriodRepository policyPeriodRepository;
    private final PolicyLegacyIdMapRepository policyLegacyIdMapRepository;
    private final ProductRepository productRepository;
    private final PlanRepository planRepository;
    private final Environment environment;
    private final LegacyPolicyMigrationSafetyGuard safetyGuard;
    private final TransactionTemplate transactionTemplate;

    @PostConstruct
    void init() {
        // Intentionally left blank. Do not override the injected TransactionTemplate's
        // propagation behavior here so callers (tests or higher-level orchestrators)
        // can control transaction boundaries. Overriding to REQUIRES_NEW caused
        // visibility issues where a caller's uncommitted setup was not visible to
        // the executor's apply transaction.
    }

    public LegacyPolicyMigrationExecutionResult execute(
            List<LegacyPolicyMigrationDiscovery.LegacyPolicyRecord> legacyPolicies,
            LegacyPolicyMigrationExecutionMode mode) {

        if (legacyPolicies == null || legacyPolicies.isEmpty()) {
            return LegacyPolicyMigrationExecutionResult.empty(mode == null ? LegacyPolicyMigrationExecutionMode.DRY_RUN : mode);
        }

        if (mode == null) {
            mode = LegacyPolicyMigrationExecutionMode.DRY_RUN;
        }

        if (mode == LegacyPolicyMigrationExecutionMode.APPLY) {
            boolean allowed = safetyGuard.isApplyAllowed(environment, environment.getProperty("spring.datasource.url"));
            if (!allowed) {
                return blockedApplyResult(legacyPolicies, mode, "APPLY mode rejected: only disposable Testcontainers/local test databases are permitted for migration execution.");
            }
        }

        List<LegacyPolicyMigrationOutcome> outcomes = new ArrayList<>();
        int discovered = legacyPolicies.size();
        int safe = 0;
        int alreadyMapped = 0;
        int planned = 0;
        int migrated = 0;
        int blocked = 0;
        int ambiguous = 0;
        int unmatched = 0;
        int invalid = 0;
        int customerServiceOnly = 0;
        int failed = 0;

        for (LegacyPolicyMigrationDiscovery.LegacyPolicyRecord legacyPolicy : legacyPolicies) {
            LegacyPolicyMigrationDiscovery.LegacyPolicyMigrationDecision decision = LegacyPolicyMigrationDiscovery.classify(
                    legacyPolicy,
                    policyContractRepository.findAll(),
                    policyLegacyIdMapRepository.findAll(),
                    policyPeriodRepository.findAll());

            if (decision == null) {
                failed++;
                outcomes.add(new LegacyPolicyMigrationOutcome(
                        legacyPolicy.legacySource(),
                        legacyPolicy.legacyPolicyId(),
                        null,
                        "FAILED",
                        null,
                        null,
                        null,
                        "UNKNOWN",
                        List.of("Classification could not be produced"),
                        "No deterministic migration decision was created.",
                        false,
                        false));
                continue;
            }

            switch (decision.classification()) {
                case SAFE_MATCH -> safe++;
                case ALREADY_MAPPED -> alreadyMapped++;
                case AMBIGUOUS -> ambiguous++;
                case UNMATCHED -> unmatched++;
                case INVALID -> invalid++;
                case CUSTOMER_SERVICE_ONLY -> customerServiceOnly++;
                default -> failed++;
            }

            if (!LegacyPolicyMigrationEngine.isAllowedClassification(decision.classification())) {
                blocked++;
                outcomes.add(new LegacyPolicyMigrationOutcome(
                        legacyPolicy.legacySource(),
                        legacyPolicy.legacyPolicyId(),
                        decision.classification(),
                        "BLOCKED",
                        null,
                        null,
                        null,
                        "REJECTED",
                        List.of("Unsafe classification is not permitted for automatic migration. " + decision.reason()),
                        decision.reason(),
                        false,
                        false));
                continue;
            }

            planned++;
            LegacyPolicyMigrationEngine.LegacyPolicyMigrationPlan plan = LegacyPolicyMigrationEngine.planLegacyMigration(
                    legacyPolicy,
                    policyContractRepository.findAll(),
                    policyPeriodRepository.findAll(),
                    policyLegacyIdMapRepository.findAll());

            if (!plan.allowedForAutomaticMigration()) {
                blocked++;
                outcomes.add(new LegacyPolicyMigrationOutcome(
                        legacyPolicy.legacySource(),
                        legacyPolicy.legacyPolicyId(),
                        decision.classification(),
                        "BLOCKED",
                        null,
                        null,
                        null,
                        "REJECTED",
                        List.of(plan.action()),
                        plan.action(),
                        false,
                        false));
                continue;
            }

            if (mode == LegacyPolicyMigrationExecutionMode.DRY_RUN) {
                outcomes.add(new LegacyPolicyMigrationOutcome(
                        legacyPolicy.legacySource(),
                        legacyPolicy.legacyPolicyId(),
                        decision.classification(),
                        "PLANNED",
                        plan.targetContract() != null ? plan.targetContract().getId() : null,
                        plan.preservedPolicyNumber(),
                        plan.initialPeriod() != null ? plan.initialPeriod().getId() : null,
                        plan.legacyIdMapping() != null ? "PENDING" : "NOT_CREATED",
                        List.of(),
                        "Dry-run only; no target writes executed.",
                        decision.classification() == LegacyPolicyMigrationDiscovery.LegacyPolicyClassification.ALREADY_MAPPED,
                        false));
                continue;
            }

            LegacyPolicyMigrationOutcome applied;
            try {
                // Use a non-shared TransactionTemplate copy so tests or other callers
                // mutating the injected TransactionTemplate do not affect executor behavior.
                TransactionTemplate localTx = new TransactionTemplate(transactionTemplate.getTransactionManager());
                localTx.setIsolationLevel(transactionTemplate.getIsolationLevel());
                localTx.setTimeout(transactionTemplate.getTimeout());
                localTx.setName(transactionTemplate.getName());
                // Use REQUIRED to join caller transaction when present, otherwise start a new transaction.
                localTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
                applied = localTx.execute(status -> applySingleLegacyPolicy(legacyPolicy, decision, plan));
            } catch (UnexpectedRollbackException ex) {
                applied = new LegacyPolicyMigrationOutcome(
                        legacyPolicy.legacySource(),
                        legacyPolicy.legacyPolicyId(),
                        decision == null ? null : decision.classification(),
                        "BLOCKED",
                        null,
                        null,
                        null,
                        "REJECTED",
                        List.of(ex.getMessage()),
                        "Concurrent migration conflict triggered a rollback; this migration did not persist.",
                        false,
                        false);
            }

            outcomes.add(applied);
            if ("MIGRATED".equals(applied.outcome())) {
                migrated++;
            } else if (!"ALREADY_MAPPED".equals(applied.outcome())) {
                blocked++;
            }
        }

        return new LegacyPolicyMigrationExecutionResult(
                mode,
                discovered,
                safe,
                alreadyMapped,
                planned,
                migrated,
                blocked,
                ambiguous,
                unmatched,
                invalid,
                customerServiceOnly,
                failed,
                outcomes);
    }

    public LegacyPolicyMigrationOutcome applySingleLegacyPolicy(
            LegacyPolicyMigrationDiscovery.LegacyPolicyRecord legacyPolicy,
            LegacyPolicyMigrationDiscovery.LegacyPolicyMigrationDecision decision,
            LegacyPolicyMigrationEngine.LegacyPolicyMigrationPlan plan) {

        if (legacyPolicy == null) {
            return new LegacyPolicyMigrationOutcome(null, null, null, "FAILED", null, null, null,
                    "UNKNOWN", List.of("Legacy policy is required"), "No legacy policy supplied.", false, false);
        }

        if (decision == null || !LegacyPolicyMigrationEngine.isAllowedClassification(decision.classification())) {
            return new LegacyPolicyMigrationOutcome(
                    legacyPolicy.legacySource(),
                    legacyPolicy.legacyPolicyId(),
                    decision == null ? null : decision.classification(),
                    "BLOCKED",
                    null,
                    null,
                    null,
                    "REJECTED",
                    List.of("Unsafe classification for apply"),
                    "Automatic apply is blocked for this classification.",
                    false,
                    false);
        }

        if (plan == null || !plan.allowedForAutomaticMigration()) {
            return new LegacyPolicyMigrationOutcome(
                    legacyPolicy.legacySource(),
                    legacyPolicy.legacyPolicyId(),
                    decision.classification(),
                    "BLOCKED",
                    null,
                    null,
                    null,
                    "REJECTED",
                    List.of(plan == null ? "No plan available" : plan.action()),
                    plan == null ? "No execution plan was available." : plan.action(),
                    false,
                    false);
        }

        if (plan.targetContract() == null) {
            return new LegacyPolicyMigrationOutcome(
                    legacyPolicy.legacySource(),
                    legacyPolicy.legacyPolicyId(),
                    decision.classification(),
                    "BLOCKED",
                    null,
                    null,
                    null,
                    "REJECTED",
                    List.of("No deterministic target contract for apply"),
                    "The planner produced no deterministic target contract.",
                    false,
                    false);
        }

        PolicyContract planTarget = plan.targetContract();
        log.debug("Attempting to lock target contract candidate: id={}, customerId={}, policyNumber={}",
                planTarget == null ? null : planTarget.getId(),
                planTarget == null ? null : planTarget.getCustomerId(),
                planTarget == null ? null : planTarget.getPolicyNumber());

        List<PolicyContract> lockedTargetContracts = policyContractRepository.findForUpdateSkipLocked(
                planTarget.getCustomerId(), planTarget.getPolicyNumber());
        log.debug("Locked target contracts count: {}", lockedTargetContracts == null ? 0 : lockedTargetContracts.size());
        if (lockedTargetContracts.isEmpty()) {
            PolicyContract existingMappingTarget = resolveExistingMappingTarget(legacyPolicy);
            log.debug("Existing mapping target resolved: {}", existingMappingTarget == null ? null : existingMappingTarget.getId());
            if (existingMappingTarget != null) {
                return new LegacyPolicyMigrationOutcome(
                        legacyPolicy.legacySource(),
                        legacyPolicy.legacyPolicyId(),
                        decision.classification(),
                        "ALREADY_MAPPED",
                        existingMappingTarget.getId(),
                        existingMappingTarget.getPolicyNumber(),
                        null,
                        "EXISTS",
                        List.of(),
                        "Another migration already holds the authoritative contract lock; source-qualified mapping is already established.",
                        true,
                        true);
            }
            return new LegacyPolicyMigrationOutcome(
                    legacyPolicy.legacySource(),
                    legacyPolicy.legacyPolicyId(),
                    decision.classification(),
                    "BLOCKED",
                    null,
                    null,
                    null,
                    "REJECTED",
                    List.of("Target contract is unavailable for locking during migration."),
                    "The authoritative contract lock could not be acquired; the migration was deferred.",
                    false,
                    false);
        }

        PolicyContract existingMappingTarget = resolveExistingMappingTarget(legacyPolicy);
        if (existingMappingTarget != null) {
            return new LegacyPolicyMigrationOutcome(
                    legacyPolicy.legacySource(),
                    legacyPolicy.legacyPolicyId(),
                    decision.classification(),
                    "ALREADY_MAPPED",
                    existingMappingTarget.getId(),
                    existingMappingTarget.getPolicyNumber(),
                    null,
                    "EXISTS",
                    List.of(),
                    "Legacy source-qualified mapping already exists; duplicate apply skipped.",
                    true,
                    true);
        }

        try {
            PolicyContract authoritativeContract = lockedTargetContracts.stream()
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseGet(() -> resolveAuthoritativeTargetContract(planTarget));
            if (authoritativeContract == null) {
                throw new IllegalStateException("No authoritative contract row is available for the locked target policy.");
            }

            PolicyContract targetContract = authoritativeContract;
            if (!LegacyPolicyMigrationDiscovery.validateContractAndPeriodInvariants(targetContract, policyPeriodRepository.findAll(), policyContractRepository.findAll())) {
                throw new IllegalStateException("Target contract invariants failed revalidation before persistence.");
            }

            PolicyContract samePolicyNumberContract = policyContractRepository.findByCustomerIdAndPolicyNumber(
                    targetContract.getCustomerId(), targetContract.getPolicyNumber()).orElse(null);
            if (samePolicyNumberContract != null && !Objects.equals(samePolicyNumberContract.getId(), targetContract.getId())) {
                throw new IllegalStateException("Policy number conflict is present for the same customer and cannot be migrated safely.");
            }

            targetContract = policyContractRepository.saveAndFlush(targetContract);

            PolicyPeriod targetPeriod = buildTargetPeriod(targetContract, legacyPolicy, plan.initialPeriod());
            if (targetPeriod != null) {
                targetPeriod = policyPeriodRepository.saveAndFlush(targetPeriod);
                targetContract.setCurrentPolicyPeriod(targetPeriod);
                policyContractRepository.save(targetContract);
            }

            PolicyLegacyIdMap mapping = new PolicyLegacyIdMap();
            mapping.setLegacySource(legacyPolicy.legacySource());
            mapping.setLegacyPolicyId(legacyPolicy.legacyPolicyId());
            mapping.setPolicyContract(targetContract);
            mapping.setLegacyRecordType(legacyPolicy.legacyRecordType() == null ? LegacyPolicyMigrationDiscovery.POLICY_RECORD_TYPE : legacyPolicy.legacyRecordType());
            policyLegacyIdMapRepository.saveAndFlush(mapping);

            return new LegacyPolicyMigrationOutcome(
                    legacyPolicy.legacySource(),
                    legacyPolicy.legacyPolicyId(),
                    decision.classification(),
                    "MIGRATED",
                    targetContract.getId(),
                    targetContract.getPolicyNumber(),
                    targetPeriod != null ? targetPeriod.getId() : null,
                    "CREATED",
                    List.of(),
                    "Legacy policy migrated successfully under the controlled executor.",
                    false,
                    true);
        } catch (DataIntegrityViolationException | CannotAcquireLockException | IllegalStateException ex) {
            return new LegacyPolicyMigrationOutcome(
                    legacyPolicy.legacySource(),
                    legacyPolicy.legacyPolicyId(),
                    decision.classification(),
                    "BLOCKED",
                    null,
                    null,
                    null,
                    "REJECTED",
                    List.of(ex.getMessage()),
                    "Apply was rejected because the record failed revalidation, serialization checks, or database uniqueness constraints.",
                    false,
                    false);
        }
    }

    private PolicyContract resolveExistingMappingTarget(LegacyPolicyMigrationDiscovery.LegacyPolicyRecord legacyPolicy) {
        if (legacyPolicy == null || legacyPolicy.legacySource() == null || legacyPolicy.legacyPolicyId() == null) {
            return null;
        }

        return policyLegacyIdMapRepository.findByLegacySourceAndLegacyPolicyId(legacyPolicy.legacySource(), legacyPolicy.legacyPolicyId())
                .map(PolicyLegacyIdMap::getPolicyContract)
                .orElse(null);
    }

    private PolicyContract resolveAuthoritativeTargetContract(PolicyContract candidateContract) {
        if (candidateContract == null || candidateContract.getCustomerId() == null || candidateContract.getPolicyNumber() == null) {
            return candidateContract;
        }

        return policyContractRepository.findByCustomerIdAndPolicyNumber(
                candidateContract.getCustomerId(), candidateContract.getPolicyNumber()).orElse(candidateContract);
    }

    private PolicyPeriod buildTargetPeriod(
            PolicyContract targetContract,
            LegacyPolicyMigrationDiscovery.LegacyPolicyRecord legacyPolicy,
            PolicyPeriod initialPeriod) {

        if (targetContract == null || legacyPolicy == null) {
            return null;
        }

        if (legacyPolicy.effectiveDate() == null || legacyPolicy.expirationDate() == null) {
            return null;
        }

        if (!legacyPolicy.effectiveDate().isBefore(legacyPolicy.expirationDate())) {
            return null;
        }

        Long planId = resolvePlanIdForLegacyPolicy(legacyPolicy);
        if (planId == null) {
            return null;
        }

        PolicyPeriod period = new PolicyPeriod();
        period.setPolicyContract(targetContract);
        period.setPreviousPolicyPeriod(initialPeriod);
        period.setPlanId(planId);
        period.setRenewalSequence(0);
        period.setStatus("ACTIVE");
        period.setEffectiveDate(legacyPolicy.effectiveDate());
        period.setExpirationDate(legacyPolicy.expirationDate());
        period.setRenewalDate(legacyPolicy.expirationDate());
        period.setActivatedAt(legacyPolicy.effectiveDate());
        return period;
    }

    private Long resolvePlanIdForLegacyPolicy(LegacyPolicyMigrationDiscovery.LegacyPolicyRecord legacyPolicy) {
        if (legacyPolicy == null || legacyPolicy.productCode() == null || legacyPolicy.productCode().isBlank()) {
            return planRepository.findAll().stream()
                    .filter(Objects::nonNull)
                    .min(Comparator.comparing(Plan::getId))
                    .map(Plan::getId)
                    .orElse(null);
        }

        Product product = productRepository.findByCode(legacyPolicy.productCode()).orElse(null);
        if (product == null) {
            return null;
        }

        List<Plan> productPlans = planRepository.findByProductIdAndStatus(product.getId(), "ACTIVE");
        if (productPlans.isEmpty()) {
            return null;
        }

        return productPlans.stream()
                .filter(Objects::nonNull)
                .min(Comparator.comparing(Plan::getId))
                .map(Plan::getId)
                .orElse(null);
    }

    private LegacyPolicyMigrationExecutionResult blockedApplyResult(
            List<LegacyPolicyMigrationDiscovery.LegacyPolicyRecord> legacyPolicies,
            LegacyPolicyMigrationExecutionMode mode,
            String reason) {

        List<LegacyPolicyMigrationOutcome> outcomes = legacyPolicies.stream()
                .map(legacyPolicy -> new LegacyPolicyMigrationOutcome(
                        legacyPolicy.legacySource(),
                        legacyPolicy.legacyPolicyId(),
                        null,
                        "BLOCKED",
                        null,
                        null,
                        null,
                        "REJECTED",
                        List.of(reason),
                        reason,
                        false,
                        false))
                .toList();

        return new LegacyPolicyMigrationExecutionResult(mode, legacyPolicies.size(), 0, 0, 0, 0, legacyPolicies.size(), 0, 0, 0, 0, 0, outcomes);
    }
}
