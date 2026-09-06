package com.claimassist.platform.policy_service.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.DockerClientFactory;
import org.junit.jupiter.api.Assumptions;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest {

    @Test
    void flywayMigrations_shouldExecuteSuccessfully() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available, skipping Flyway migration test");

        try (PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
                DockerImageName.parse("postgres:16-alpine")
        )) {
            postgresContainer.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(postgresContainer.getJdbcUrl(),
                               postgresContainer.getUsername(),
                               postgresContainer.getPassword())
                    .locations("classpath:db/migration")
                    .cleanDisabled(false)
                    .load();

            flyway.clean();
            var migrateResult = flyway.migrate();

            // Expect the single V1 migration in this module
            assertThat(migrateResult.migrationsExecuted).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void databaseSchema_shouldContainPolicyTables() throws SQLException {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available, skipping Flyway migration test");

        try (PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
                DockerImageName.parse("postgres:16-alpine")
        )) {
            postgresContainer.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(postgresContainer.getJdbcUrl(),
                               postgresContainer.getUsername(),
                               postgresContainer.getPassword())
                    .locations("classpath:db/migration")
                    .cleanDisabled(false)
                    .load();

            flyway.clean();
            flyway.migrate();

            try (Connection conn = postgresContainer.createConnection("");
                 Statement stmt = conn.createStatement();) {

                ResultSet rs = stmt.executeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name"
                );

                StringBuilder tables = new StringBuilder();
                while (rs.next()) {
                    tables.append(rs.getString("table_name")).append(", ");
                }

                assertThat(tables.toString()).contains("policies");
                assertThat(tables.toString()).contains("plans");
                assertThat(tables.toString()).contains("products");
                assertThat(tables.toString()).contains("policy_version");
                assertThat(tables.toString()).contains("policy_version_coverage");
            }
        }
    }
}
