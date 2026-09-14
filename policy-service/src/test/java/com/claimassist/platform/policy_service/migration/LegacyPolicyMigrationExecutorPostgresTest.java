package com.claimassist.platform.policy_service.migration;

import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.PolicyContract;
import com.claimassist.platform.policy_service.entity.PolicyLegacyIdMap;
import com.claimassist.platform.policy_service.entity.PolicyPeriod;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.entity.ProductType;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.PolicyContractRepository;
import com.claimassist.platform.policy_service.repository.PolicyLegacyIdMapRepository;
import com.claimassist.platform.policy_service.repository.PolicyPeriodRepository;
import com.claimassist.platform.policy_service.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({LegacyPolicyMigrationExecutor.class, LegacyPolicyMigrationSafetyGuard.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class LegacyPolicyMigrationExecutorPostgresTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("policy_migration_pg")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private LegacyPolicyMigrationExecutor executor;

    @Autowired
    private PolicyContractRepository policyContractRepository;

    @Autowired
    private PolicyPeriodRepository policyPeriodRepository;

    @Autowired
    private PolicyLegacyIdMapRepository policyLegacyIdMapRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Product product;
    private Plan plan;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("UPDATE policy_contracts SET current_policy_period_id = NULL");
        jdbcTemplate.update("DELETE FROM policy_legacy_id_map");
        jdbcTemplate.update("DELETE FROM policy_periods");
        jdbcTemplate.update("DELETE FROM policy_contracts");
        jdbcTemplate.update("DELETE FROM plans");
        jdbcTemplate.update("DELETE FROM products");

        product = productRepository.save(Product.builder()
                .code("AUTO")
                .name("Auto Insurance")
                .type(ProductType.AUTO)
                .status("ACTIVE")
                .description("Vehicle cover")
                .build());

        plan = planRepository.save(Plan.builder()
                .product(product)
                .code("AUTO_BASIC")
                .name("Basic Auto")
                .status("ACTIVE")
                .annualPremiumCents(25000L)
                .deductibleCents(4000L)
                .coverageLimitCents(500000L)
                .currency("INR")
                .build());
    }

    @Test
    void safeMatch_apply_creates_contract_period_and_mapping() {
        createMatchingContract("POL-LEG-101", 42L);
        LegacyPolicyMigrationDiscovery.LegacyPolicyRecord legacyRecord = legacyPolicyRecord(101L, "POL-LEG-101");

        LegacyPolicyMigrationExecutionResult result = executor.execute(List.of(legacyRecord), LegacyPolicyMigrationExecutionMode.APPLY);

        assertThat(result.migrated()).isEqualTo(1);
        assertThat(result.outcomes()).hasSize(1);
        assertThat(result.outcomes().get(0).outcome()).isEqualTo("MIGRATED");
        assertThat(policyContractRepository.count()).isEqualTo(1);
        assertThat(policyPeriodRepository.count()).isEqualTo(1);
        assertThat(policyLegacyIdMapRepository.count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_events", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM purchases", Long.class)).isZero();

        PolicyContract contract = policyContractRepository.findAll().get(0);
        assertThat(contract.getPolicyNumber()).isEqualTo("POL-LEG-101");
        assertThat(contract.getCurrentPolicyPeriod()).isNotNull();
        assertThat(policyLegacyIdMapRepository.findByLegacySourceAndLegacyPolicyId(LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE, 101L)).isPresent();
    }

    @Test
    void dryRun_leaves_target_tables_unchanged() {
        createMatchingContract("POL-LEG-202", 42L);
        LegacyPolicyMigrationDiscovery.LegacyPolicyRecord legacyRecord = legacyPolicyRecord(202L, "POL-LEG-202");

        long beforeContractCount = policyContractRepository.count();
        long beforePeriodCount = policyPeriodRepository.count();
        long beforeMappingCount = policyLegacyIdMapRepository.count();

        LegacyPolicyMigrationExecutionResult result = executor.execute(List.of(legacyRecord), LegacyPolicyMigrationExecutionMode.DRY_RUN);

        assertThat(result.mode()).isEqualTo(LegacyPolicyMigrationExecutionMode.DRY_RUN);
        assertThat(result.planned()).isEqualTo(1);
        assertThat(policyContractRepository.count()).isEqualTo(beforeContractCount);
        assertThat(policyPeriodRepository.count()).isEqualTo(beforePeriodCount);
        assertThat(policyLegacyIdMapRepository.count()).isEqualTo(beforeMappingCount);
        assertThat(result.outcomes()).hasSize(1);
    }

    @Test
    void invalid_record_is_blocked_without_persistence() {
        LegacyPolicyMigrationDiscovery.LegacyPolicyRecord invalid = new LegacyPolicyMigrationDiscovery.LegacyPolicyRecord(
                LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE,
                404L,
                42L,
                "POL-INVALID",
                "AUTO",
                "AUTO_BASIC",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:00:00Z"),
                "ACTIVE",
                "POLICY");

        LegacyPolicyMigrationExecutionResult result = executor.execute(List.of(invalid), LegacyPolicyMigrationExecutionMode.APPLY);

        assertThat(result.blocked()).isGreaterThanOrEqualTo(1);
        assertThat(policyContractRepository.count()).isZero();
        assertThat(policyPeriodRepository.count()).isZero();
        assertThat(policyLegacyIdMapRepository.count()).isZero();
    }

    @Test
    void alreadyMapped_is_not_duplicated() {
        PolicyContract contract = createMatchingContract("POL-LEG-303", 42L);

        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.executeWithoutResult(status -> {
            PolicyLegacyIdMap mapping = new PolicyLegacyIdMap();
            mapping.setLegacySource(LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE);
            mapping.setLegacyPolicyId(303L);
            mapping.setPolicyContract(contract);
            mapping.setLegacyRecordType(LegacyPolicyMigrationDiscovery.POLICY_RECORD_TYPE);
            policyLegacyIdMapRepository.saveAndFlush(mapping);
        });

        LegacyPolicyMigrationDiscovery.LegacyPolicyRecord legacyRecord = legacyPolicyRecord(303L, "POL-LEG-303");

        LegacyPolicyMigrationExecutionResult result = executor.execute(List.of(legacyRecord), LegacyPolicyMigrationExecutionMode.APPLY);

        assertThat(result.alreadyMapped()).isEqualTo(1);
        assertThat(policyContractRepository.count()).isEqualTo(1);
        assertThat(policyLegacyIdMapRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrent_apply_only_one_successful_migration_occurs() throws Exception {
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.executeWithoutResult(status -> createMatchingContract("POL-LEG-505", 42L));
        assertThat(policyContractRepository.findByCustomerIdAndPolicyNumber(42L, "POL-LEG-505")).isPresent();
        LegacyPolicyMigrationDiscovery.LegacyPolicyRecord legacyRecord = legacyPolicyRecord(505L, "POL-LEG-505");

        CountDownLatch workerAHasMigrated = new CountDownLatch(1);
        CountDownLatch releaseWorkerACommit = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        Future<?> workerAFuture = executorService.submit(() -> {
            TransactionTemplate workerATx = new TransactionTemplate(transactionTemplate.getTransactionManager());
            workerATx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            workerATx.executeWithoutResult(status -> {
                PolicyContract lockedContract = policyContractRepository.findForUpdateSkipLocked(42L, "POL-LEG-505")
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Worker A could not lock the target contract."));

                PolicyPeriod period = new PolicyPeriod();
                period.setPolicyContract(lockedContract);
                period.setPreviousPolicyPeriod(null);
                period.setPlanId(plan.getId());
                period.setRenewalSequence(0);
                period.setStatus("ACTIVE");
                period.setEffectiveDate(legacyRecord.effectiveDate());
                period.setExpirationDate(legacyRecord.expirationDate());
                period.setRenewalDate(legacyRecord.expirationDate());
                period.setActivatedAt(legacyRecord.effectiveDate());
                period = policyPeriodRepository.saveAndFlush(period);

                lockedContract.setCurrentPolicyPeriod(period);
                policyContractRepository.saveAndFlush(lockedContract);

                PolicyLegacyIdMap mapping = new PolicyLegacyIdMap();
                mapping.setLegacySource(legacyRecord.legacySource());
                mapping.setLegacyPolicyId(legacyRecord.legacyPolicyId());
                mapping.setPolicyContract(lockedContract);
                mapping.setLegacyRecordType(legacyRecord.legacyRecordType());
                policyLegacyIdMapRepository.saveAndFlush(mapping);

                workerAHasMigrated.countDown();
                try {
                    releaseWorkerACommit.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Worker A was interrupted while holding the migration transaction open.", e);
                }
            });
            return null;
        });

        workerAHasMigrated.await();

        Future<List<PolicyContract>> skipLockedProbe = executorService.submit(() -> {
            TransactionTemplate probeTx = new TransactionTemplate(transactionTemplate.getTransactionManager());
            probeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return probeTx.execute(status -> policyContractRepository.findForUpdateSkipLocked(42L, "POL-LEG-505"));
        });

        List<PolicyContract> skippedRows = skipLockedProbe.get(10, TimeUnit.SECONDS);
        assertThat(skippedRows).isEmpty();

        releaseWorkerACommit.countDown();
        workerAFuture.get();

        LegacyPolicyMigrationExecutionResult recheckResult = executor.execute(List.of(legacyRecord), LegacyPolicyMigrationExecutionMode.APPLY);
        executorService.shutdown();

        assertThat(recheckResult.alreadyMapped()).isEqualTo(1);
        assertThat(policyContractRepository.count()).isEqualTo(1);
        assertThat(policyPeriodRepository.count()).isEqualTo(1);
        assertThat(policyLegacyIdMapRepository.count()).isEqualTo(1);
    }

    private PolicyContract createMatchingContract(String policyNumber, Long customerId) {
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate.execute(status -> policyContractRepository.saveAndFlush(PolicyContract.builder()
                .customerId(customerId)
                .productId(product.getId())
                .policyNumber(policyNumber)
                .status("ACTIVE")
                .build()));
    }

    private LegacyPolicyMigrationDiscovery.LegacyPolicyRecord legacyPolicyRecord(Long legacyPolicyId, String policyNumber) {
        return new LegacyPolicyMigrationDiscovery.LegacyPolicyRecord(
                LegacyPolicyMigrationDiscovery.CUSTOMER_SERVICE_SOURCE,
                legacyPolicyId,
                42L,
                policyNumber,
                "AUTO",
                "AUTO_BASIC",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                "ACTIVE",
                "POLICY");
    }
}
