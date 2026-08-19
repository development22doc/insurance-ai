package com.claimassist.platform.customer_service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.repository.CustomerRepository;

import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("testcontainers")
@Import(PostgresIntegrationTestConfig.class)
class CustomerPostgresIT {

    @Resource
    CustomerRepository customerRepository;

    @Resource
    DataSource dataSource;

    @Resource
    JdbcTemplate jdbcTemplate;

    @Resource
    EntityManager entityManager;

    @Test
    void crud_createAndReadCustomer_withPostgres() throws SQLException {
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

        Customer customer = new Customer();
        customer.setUsername("postgres-test@example.com");
        customer.setFullName("PostgreSQL Test User");
        customer.setKeycloakId("postgres-keycloak-id");

        Customer saved = customerRepository.save(customer);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsername()).isEqualTo("postgres-test@example.com");
        assertThat(saved.getFullName()).isEqualTo("PostgreSQL Test User");

        Customer found = customerRepository.findById(saved.getId()).orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getUsername()).isEqualTo("postgres-test@example.com");
    }
}
