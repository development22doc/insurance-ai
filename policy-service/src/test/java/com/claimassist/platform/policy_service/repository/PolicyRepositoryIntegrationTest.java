package com.claimassist.platform.policy_service.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimassist.platform.policy_service.dto.PolicyCoverageProjection;

@Testcontainers
@org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PolicyRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("testdb").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        // Disable Spring Boot auto-run of Flyway; run migrations manually to avoid auto-detection errors
        r.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PolicyRepository policyRepository;

    @Test
    void endToEnd_policyCoverageProjection_returnsExpected() {
        // Run Flyway migrations against the started container
        org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();

        // Insert product
        Long productId = jdbc.queryForObject("INSERT INTO products(code,name) VALUES('AUTO','Auto') RETURNING id", Long.class);
        // Insert plan
        Long planId = jdbc.queryForObject("INSERT INTO plans(product_id,code,name,active,deductible_cents,coverage_limit_cents) VALUES(?,?,?,?,?,?) RETURNING id",
                Long.class, productId, "BASIC", "Basic Plan", true, 0L, 100000L);
        // Insert coverage (multiple coverages possible)
        Long coverage1 = jdbc.queryForObject("INSERT INTO coverages(plan_id,code,name,limit_cents,deductible_cents) VALUES(?,?,?,?,?) RETURNING id",
                Long.class, planId, "COV1", "Coverage 1", 50000L, 1000L);
        Long coverage2 = jdbc.queryForObject("INSERT INTO coverages(plan_id,code,name,limit_cents,deductible_cents) VALUES(?,?,?,?,?) RETURNING id",
                Long.class, planId, "COV2", "Coverage 2", 200000L, 5000L);

        // Insert policy
        Long policyId = jdbc.queryForObject("INSERT INTO policies(policy_number,customer_id,coverage_plan_id,status,effective_date,renewal_date) VALUES(?,?,?,?, '2027-01-01'::timestamp, '2027-01-01'::timestamp) RETURNING id",
                Long.class, "POL-1", 77L, planId, "ACTIVE");

                // Expect deterministic renewal_date formatting


        // Insert policy_version v1
        Long pv1 = jdbc.queryForObject("INSERT INTO policy_version(policy_id,version_number,plan_id,premium_cents,deductible_cents,coverage_limit_cents,effective_from) VALUES(?,?,?,?,?,?,now()) RETURNING id",
                Long.class, policyId, 1, planId, 1000L, 0L, 100000L);
        // link coverages to version
        jdbc.update("INSERT INTO policy_version_coverage(policy_version_id,coverage_id,limit_cents,deductible_cents) VALUES(?,?,?,?)",
                pv1, coverage1, 50000L, 1000L);
        jdbc.update("INSERT INTO policy_version_coverage(policy_version_id,coverage_id,limit_cents,deductible_cents) VALUES(?,?,?,?)",
                pv1, coverage2, 200000L, 5000L);

        // Execute repository query: customer id must match
        PolicyCoverageProjection proj = policyRepository.findPolicyCoverageProjectionByPolicyIdAndCustomerId(policyId, 77L);
        assertThat(proj).isNotNull();
        assertThat(proj.getPolicyId()).isEqualTo(policyId);
        assertThat(proj.getPolicyNumber()).isEqualTo("POL-1");
        assertThat(proj.getProductType()).isEqualTo("AUTO");
        assertThat(proj.getCoveragePlanName()).isEqualTo("Basic Plan");
        // deductible and coverageLimit map from policy_version if present
        assertThat(proj.getDeductibleCents()).isEqualTo(0L);
        assertThat(proj.getCoverageLimitCents()).isEqualTo(100000L);
        // renewalDate should be ISO YYYY-MM-DD per repository convention
        assertThat(proj.getRenewalDate()).isEqualTo("2027-01-01");

        // Verify that repository currently returns single-row projection (it does) and does not include multiple coverage rows.
        // The policy_version_coverage entries exist, but the projection returns plan-level aggregated fields; separate coverage rows are not returned by this query.
    }
}
