package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.PolicyContract;
import com.claimassist.platform.policy_service.entity.PolicyPeriod;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.entity.ProductType;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.PolicyContractRepository;
import com.claimassist.platform.policy_service.repository.PolicyPeriodRepository;
import com.claimassist.platform.policy_service.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PolicyCoverageService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PolicyReadPathPostgresValidationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("policy_service_pg")
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
    private PolicyContractRepository policyContractRepository;

    @Autowired
    private PolicyPeriodRepository policyPeriodRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PolicyCoverageService policyCoverageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    private PolicyContract contract;
    private PolicyPeriod initialPeriod;
    private PolicyPeriod renewalPeriod;

    @BeforeEach
    void setUp() {
        Product product = productRepository.save(Product.builder()
                .code("AUTO")
                .name("Auto Insurance")
                .type(ProductType.AUTO)
                .status("ACTIVE")
                .description("Vehicle cover")
                .build());

        Plan plan = planRepository.save(Plan.builder()
                .product(product)
                .code("AUTO_BASIC")
                .name("Basic Auto")
                .status("ACTIVE")
                .annualPremiumCents(25000L)
                .deductibleCents(4000L)
                .coverageLimitCents(500000L)
                .currency("INR")
                .build());

        contract = policyContractRepository.saveAndFlush(PolicyContract.builder()
                .customerId(42L)
                .productId(product.getId())
                .policyNumber("POL-42-200")
                .status("ACTIVE")
                .build());

        initialPeriod = policyPeriodRepository.saveAndFlush(PolicyPeriod.builder()
                .policyContract(contract)
                .planId(plan.getId())
                .renewalSequence(0)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2026-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2027-01-01T00:00:00Z"))
                .renewalDate(Instant.parse("2027-01-01T00:00:00Z"))
                .activatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build());

        renewalPeriod = policyPeriodRepository.saveAndFlush(PolicyPeriod.builder()
                .policyContract(contract)
                .previousPolicyPeriod(initialPeriod)
                .planId(plan.getId())
                .renewalSequence(1)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2027-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2028-01-01T00:00:00Z"))
                .renewalDate(Instant.parse("2028-01-01T00:00:00Z"))
                .activatedAt(Instant.parse("2027-01-01T00:00:00Z"))
                .build());

        contract.setCurrentPolicyPeriod(renewalPeriod);
        contract = policyContractRepository.saveAndFlush(contract);
    }

    @Test
    void customer_policy_queries_and_coverage_semantics_are_valid_on_postgres() {
        List<PolicyContract> customerPolicies = policyContractRepository.findByCustomerIdOrderByCreatedAtDesc(42L);
        assertThat(customerPolicies).hasSize(1);
        assertThat(customerPolicies.getFirst().getPolicyNumber()).isEqualTo("POL-42-200");

        PolicyContract detail = policyContractRepository.findByIdAndCustomerId(contract.getId(), 42L).orElseThrow();
        assertThat(detail.getCurrentPolicyPeriod()).isNotNull();
        assertThat(detail.getCurrentPolicyPeriod().getRenewalSequence()).isEqualTo(1);

        List<PolicyPeriod> history = policyPeriodRepository.findByPolicyContractIdOrderByRenewalSequenceAsc(contract.getId());
        assertThat(history).extracting(PolicyPeriod::getRenewalSequence).containsExactly(0, 1);

        PolicyPeriod covered = policyCoverageService.resolveCoveringPeriod(contract.getId(), Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(covered.getRenewalSequence()).isEqualTo(0);

        PolicyPeriod current = policyCoverageService.resolveCoveringPeriod(contract.getId(), Instant.parse("2027-06-01T00:00:00Z"));
        assertThat(current.getRenewalSequence()).isEqualTo(1);

        assertThatThrownByCoverage(contract.getId(), Instant.parse("2025-12-31T23:59:59Z"));
        assertThatThrownByCoverage(contract.getId(), Instant.parse("2028-01-01T00:00:00Z"));

        String customerListPlan = explain("SELECT * FROM policy_contracts WHERE customer_id = ?", 42L);
        String policyDetailPlan = explain("SELECT * FROM policy_contracts WHERE id = ? AND customer_id = ?", contract.getId(), 42L);
        String periodHistoryPlan = explain("SELECT * FROM policy_periods WHERE policy_contract_id = ? ORDER BY renewal_sequence ASC", contract.getId());
        Instant incidentTime = Instant.parse("2026-06-01T00:00:00Z");
        String coveragePlan = explain("SELECT * FROM policy_periods WHERE policy_contract_id = ? AND effective_date <= ? AND expiration_date > ? ORDER BY effective_date DESC", contract.getId(), incidentTime, incidentTime);

        assertThat(customerListPlan).contains("policy_contracts");
        assertThat(policyDetailPlan).contains("policy_contracts");
        assertThat(periodHistoryPlan).contains("policy_periods");
        assertThat(coveragePlan).contains("policy_periods");
    }

    private void assertThatThrownByCoverage(Long policyContractId, Instant incidentTime) {
        try {
            policyCoverageService.resolveCoveringPeriod(policyContractId, incidentTime);
            throw new AssertionError("Expected no coverage for incident time " + incidentTime);
        } catch (RuntimeException ex) {
            assertThat(ex).isNotNull();
        }
    }

    private String explain(String sql, Object... args) {
        Object[] convertedArgs = Arrays.stream(args)
                .map(arg -> arg instanceof Instant instant ? java.sql.Timestamp.from(instant) : arg)
                .toArray();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("EXPLAIN (ANALYZE, BUFFERS) " + sql, convertedArgs);
        return rows.stream()
                .map(row -> row.get("QUERY PLAN").toString())
                .reduce("", (left, right) -> left + right + System.lineSeparator());
    }
}
