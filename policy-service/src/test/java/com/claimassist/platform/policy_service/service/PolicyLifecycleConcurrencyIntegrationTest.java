package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.PaymentEvent;
import com.claimassist.platform.policy_service.entity.PolicyContract;
import com.claimassist.platform.policy_service.entity.PolicyPeriod;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.entity.ProductType;
import com.claimassist.platform.policy_service.entity.Purchase;
import com.claimassist.platform.policy_service.entity.PurchaseStatus;
import com.claimassist.platform.policy_service.repository.PaymentEventRepository;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.PolicyContractRepository;
import com.claimassist.platform.policy_service.repository.PolicyPeriodRepository;
import com.claimassist.platform.policy_service.repository.ProductRepository;
import com.claimassist.platform.policy_service.repository.PurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PolicyLifecycleConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private PolicyContractRepository policyContractRepository;

    @Autowired
    private PolicyPeriodRepository policyPeriodRepository;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private PolicyLifecycleService policyLifecycleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private Product product;
    private Plan plan;
    private Purchase purchase;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        initializeSchema();
        resetData();

        product = productRepository.saveAndFlush(Product.builder()
                .code("AUTO-CONCURRENCY")
                .name("Auto Flex")
                .type(ProductType.AUTO)
                .status("ACTIVE")
                .build());

        plan = planRepository.saveAndFlush(Plan.builder()
                .product(product)
                .code("AUTO-CONCURRENCY-PLAN")
                .name("Comprehensive")
                .status("ACTIVE")
                .annualPremiumCents(50000L)
                .deductibleCents(25000L)
                .coverageLimitCents(1000000L)
                .currency("INR")
                .build());

        purchase = purchaseRepository.saveAndFlush(Purchase.builder()
                .customerId(42L)
                .plan(plan)
                .amountCents(50000L)
                .currency("INR")
                .status(PurchaseStatus.PAYMENT_PROCESSING)
                .idempotencyKey("purchase-concurrency-" + System.nanoTime())
                .initiatedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build());
    }

    @Test
    void concurrentPaymentSuccessEventsOnlyCreateOneContractAndInitialPeriod() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<CallableVoid> tasks = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            tasks.add(() -> {
                startLatch.await();
                purchaseService.applyWebhookState(purchase.getId(), "payment_intent.succeeded");
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (CallableVoid task : tasks) {
            futures.add(executor.submit(() -> {
                task.run();
                return null;
            }));
        }

        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        List<Throwable> causes = new ArrayList<>();
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof ObjectOptimisticLockingFailureException
                        || cause instanceof DataIntegrityViolationException) {
                    causes.add(cause);
                } else {
                    throw new AssertionError("Unexpected race failure", cause);
                }
            }
        }

        Purchase reloaded = purchaseRepository.findById(purchase.getId()).orElseThrow();
        assertThat(causes).isNotEmpty();
        assertThat(reloaded.getStatus()).isEqualTo(PurchaseStatus.PAID);
        assertThat(policyContractRepository.count()).isEqualTo(1L);
        assertThat(policyPeriodRepository.count()).isEqualTo(1L);

        PolicyContract contract = policyContractRepository.findAll().get(0);
        assertThat(contract.getPolicyNumber()).isEqualTo("POL-42-" + purchase.getId());
        PolicyPeriod period = policyPeriodRepository.findAll().get(0);
        assertThat(period.getRenewalSequence()).isZero();
        assertThat(period.getPreviousPolicyPeriod()).isNull();
        assertThat(contract.getCurrentPolicyPeriod()).isNotNull();
    }

    @Test
    void duplicateProviderEventIdIsRejectedByUniqueConstraint() {
        PaymentEvent first = PaymentEvent.builder()
                .purchase(purchase)
                .providerEventId("evt_duplicate_123")
                .eventType("payment_intent.succeeded")
                .eventStatus("RECEIVED")
                .payload("{}")
                .processedAt(Instant.parse("2025-01-02T00:00:00Z"))
                .build();

        PaymentEvent duplicate = PaymentEvent.builder()
                .purchase(purchase)
                .providerEventId("evt_duplicate_123")
                .eventType("payment_intent.succeeded")
                .eventStatus("RECEIVED")
                .payload("{}")
                .processedAt(Instant.parse("2025-01-03T00:00:00Z"))
                .build();

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> transactionTemplate.executeWithoutResult(status -> {
            paymentEventRepository.saveAndFlush(first);
            paymentEventRepository.saveAndFlush(duplicate);
        }));

        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(paymentEventRepository.findByProviderEventId("evt_duplicate_123")).isEmpty();
        assertThat(paymentEventRepository.count()).isZero();
    }

    @Test
    void coverageLookupUsesInclusiveEffectiveDateAndExclusiveExpirationDate() {
        PolicyContract contract = policyContractRepository.saveAndFlush(PolicyContract.builder()
                .customerId(42L)
                .productId(product.getId())
                .policyNumber("POL-42-COVERAGE")
                .status("ACTIVE")
                .build());

        PolicyPeriod period = policyPeriodRepository.saveAndFlush(PolicyPeriod.builder()
                .policyContract(contract)
                .planId(plan.getId())
                .renewalSequence(0)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2025-02-01T00:00:00Z"))
                .renewalDate(Instant.parse("2025-02-01T00:00:00Z"))
                .activatedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build());

        contract.setCurrentPolicyPeriod(period);
        policyContractRepository.saveAndFlush(contract);

        assertThat(policyPeriodRepository.findCoveringPeriodsForContractAt(contract.getId(), Instant.parse("2024-12-31T23:59:59Z"))).isEmpty();
        assertThat(policyPeriodRepository.findCoveringPeriodsForContractAt(contract.getId(), Instant.parse("2025-01-01T00:00:00Z"))).hasSize(1);
        assertThat(policyPeriodRepository.findCoveringPeriodsForContractAt(contract.getId(), Instant.parse("2025-01-15T12:00:00Z"))).hasSize(1);
        assertThat(policyPeriodRepository.findCoveringPeriodsForContractAt(contract.getId(), Instant.parse("2025-02-01T00:00:00Z"))).isEmpty();
        assertThat(policyPeriodRepository.findCoveringPeriodsForContractAt(contract.getId(), Instant.parse("2025-02-01T00:00:01Z"))).isEmpty();
    }

    @Test
    void concurrentCancellationRequestsOnlyCancelOnce() throws Exception {
        PolicyContract contract = policyContractRepository.saveAndFlush(PolicyContract.builder()
                .customerId(42L)
                .productId(product.getId())
                .policyNumber("POL-42-CONCURRENT-CANCEL")
                .status("ACTIVE")
                .build());

        PolicyPeriod period = policyPeriodRepository.saveAndFlush(PolicyPeriod.builder()
                .policyContract(contract)
                .planId(plan.getId())
                .renewalSequence(0)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .renewalDate(Instant.parse("2026-01-01T00:00:00Z"))
                .activatedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build());

        contract.setCurrentPolicyPeriod(period);
        policyContractRepository.saveAndFlush(contract);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<CallableVoid> tasks = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            tasks.add(() -> {
                startLatch.await();
                policyLifecycleService.cancelPolicy(contract.getId(), 42L, Instant.now());
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (CallableVoid task : tasks) {
            futures.add(executor.submit(() -> {
                task.run();
                return null;
            }));
        }

        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        List<Throwable> causes = new ArrayList<>();
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof ObjectOptimisticLockingFailureException
                        || cause instanceof DataIntegrityViolationException) {
                    causes.add(cause);
                } else {
                    throw new AssertionError("Unexpected race failure", cause);
                }
            }
        }

        PolicyContract reloaded = policyContractRepository.findById(contract.getId()).orElseThrow();
        PolicyPeriod reloadedPeriod = policyPeriodRepository.findById(period.getId()).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo("CANCELLED");
        assertThat(reloadedPeriod.getStatus()).isEqualTo("CANCELLED");
        assertThat(reloadedPeriod.getCancelledAt()).isNotNull();
    }

    private void initializeSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS products (
                    id BIGSERIAL PRIMARY KEY,
                    code VARCHAR(64) NOT NULL UNIQUE,
                    name VARCHAR(128) NOT NULL,
                    type VARCHAR(32) NOT NULL,
                    description VARCHAR(1000),
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plans (
                    id BIGSERIAL PRIMARY KEY,
                    product_id BIGINT NOT NULL,
                    code VARCHAR(64) NOT NULL UNIQUE,
                    name VARCHAR(128) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    annual_premium_cents BIGINT NOT NULL,
                    deductible_cents BIGINT NOT NULL,
                    coverage_limit_cents BIGINT NOT NULL,
                    currency VARCHAR(8),
                    stripe_price_id VARCHAR(128),
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS policy_contracts (
                    id BIGSERIAL PRIMARY KEY,
                    version BIGINT NOT NULL DEFAULT 0,
                    customer_id BIGINT NOT NULL,
                    product_id BIGINT NOT NULL,
                    policy_number VARCHAR(64) NOT NULL UNIQUE,
                    current_policy_period_id BIGINT,
                    status VARCHAR(32) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS policy_periods (
                    id BIGSERIAL PRIMARY KEY,
                    version BIGINT NOT NULL DEFAULT 0,
                    policy_contract_id BIGINT NOT NULL,
                    previous_policy_period_id BIGINT,
                    plan_id BIGINT NOT NULL,
                    renewal_sequence INTEGER NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    effective_date TIMESTAMPTZ NOT NULL,
                    expiration_date TIMESTAMPTZ NOT NULL,
                    renewal_date TIMESTAMPTZ,
                    activated_at TIMESTAMPTZ,
                    cancelled_at TIMESTAMPTZ,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
                """);
    }

    private void resetData() {
        jdbcTemplate.update("UPDATE policy_contracts SET current_policy_period_id = NULL WHERE current_policy_period_id IS NOT NULL");
        jdbcTemplate.update("UPDATE purchases SET policy_contract_id = NULL, source_policy_period_id = NULL, target_policy_period_id = NULL");
        jdbcTemplate.update("DELETE FROM payment_events");
        jdbcTemplate.update("DELETE FROM purchases");
        jdbcTemplate.update("DELETE FROM policy_periods");
        jdbcTemplate.update("DELETE FROM policy_contracts");
        jdbcTemplate.update("DELETE FROM plans");
        jdbcTemplate.update("DELETE FROM products");
    }

    @FunctionalInterface
    private interface CallableVoid {
        void run() throws Exception;
    }
}
