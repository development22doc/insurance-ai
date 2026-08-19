package com.claimassist.platform.claims_service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.time.Instant;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.claimassist.platform.claims_service.entity.Claim;
import com.claimassist.platform.claims_service.repository.ClaimRepository;
import com.claimassist.platform.common_lib.enums.ClaimStatus;

import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("testcontainers")
@Import(PostgresIntegrationTestConfig.class)
class ClaimPostgresIT {

    @Resource
    ClaimRepository claimRepository;

    @Resource
    DataSource dataSource;

    @Resource
    JdbcTemplate jdbcTemplate;

    @Resource
    EntityManager entityManager;

    @Test
    void crud_createAndReadClaim_withPostgres() throws SQLException {
        assertThat(dataSource.getConnection().getMetaData().getDatabaseProductName())
                .isEqualTo("PostgreSQL");
        assertThat(dataSource.getConnection().getMetaData().getURL())
                .contains("postgresql");
        assertThat(dataSource.getConnection().getMetaData().getDriverName())
                .doesNotContain("H2");

        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
        assertThat(migrationCount).isGreaterThan(0);

        assertThat(entityManager.getEntityManagerFactory().getProperties())
                .containsEntry("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

        Claim claim = new Claim();
        claim.setClaimNumber("CLM-PG-TEST-001");
        claim.setPolicyId(1L);
        claim.setIncidentType("ACCIDENT");
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setIncidentDate(Instant.now());

        Claim saved = claimRepository.save(claim);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getClaimNumber()).isEqualTo("CLM-PG-TEST-001");
        assertThat(saved.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);

        Claim found = claimRepository.findById(saved.getId()).orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getClaimNumber()).isEqualTo("CLM-PG-TEST-001");
    }
}
