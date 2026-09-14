package com.claimassist.platform.policy_service.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine")
    );

    @Test
    void flywayMigration_shouldExecuteSuccessfully() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(postgresContainer.getJdbcUrl(), postgresContainer.getUsername(), postgresContainer.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        flyway.clean();
        var migrateResult = flyway.migrate();

        assertThat(migrateResult.migrationsExecuted).isGreaterThan(0);

        try (Connection conn = postgresContainer.createConnection("");
             Statement stmt = conn.createStatement()) {
            assertTableExists(stmt, "policy_contracts");
            assertTableExists(stmt, "policy_periods");
            assertTableExists(stmt, "policy_legacy_id_map");

            assertIndexExists(stmt, "idx_policy_contracts_customer_id");
            assertIndexExists(stmt, "idx_policy_periods_contract_id");
            assertIndexExists(stmt, "idx_purchases_active_renewal_source_period");
            assertIndexExists(stmt, "idx_payment_events_purchase_id");

            assertConstraintExists(stmt, "policy_periods", "excl_policy_periods_same_contract_no_overlap");
            assertThat(getScalar(stmt, "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'purchases' AND column_name = 'purchase_type'")).isEqualTo(1L);
            assertThat(getScalar(stmt, "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'purchases' AND column_name = 'policy_contract_id'")).isEqualTo(1L);
            assertThat(getScalar(stmt, "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'purchases' AND column_name = 'source_policy_period_id'")).isEqualTo(1L);
            assertThat(getScalar(stmt, "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'purchases' AND column_name = 'target_policy_period_id'")).isEqualTo(1L);
        }
    }

    private void assertTableExists(Statement stmt, String tableName) throws Exception {
        try (ResultSet rs = stmt.executeQuery(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '" + tableName + "'")) {
            assertThat(rs.next()).isTrue();
        }
    }

    private void assertIndexExists(Statement stmt, String indexName) throws Exception {
        try (ResultSet rs = stmt.executeQuery(
                "SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = '" + indexName + "'")) {
            assertThat(rs.next()).isTrue();
        }
    }

    private void assertConstraintExists(Statement stmt, String tableName, String constraintName) throws Exception {
        try (ResultSet rs = stmt.executeQuery(
                "SELECT 1 " +
                        "FROM pg_constraint c " +
                        "JOIN pg_class t ON c.conrelid = t.oid " +
                        "JOIN pg_namespace n ON n.oid = t.relnamespace " +
                        "WHERE n.nspname = 'public' " +
                        "AND t.relname = '" + tableName + "' " +
                        "AND c.conname = '" + constraintName + "'")) {
            assertThat(rs.next()).isTrue();
        }
    }

    private long getScalar(Statement stmt, String sql) throws Exception {
        try (ResultSet rs = stmt.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }
}
