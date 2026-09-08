package com.claimassist.platform.claims_service.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FlywayMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine")
    );

    @Test
    void flywayMigrations_shouldExecuteSuccessfully() {
        Flyway flyway = Flyway.configure()
                .dataSource(postgresContainer.getJdbcUrl(),
                           postgresContainer.getUsername(),
                           postgresContainer.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        // Clean and migrate on fresh database
        flyway.clean();
        var migrateResult = flyway.migrate();

        assertThat(migrateResult.migrationsExecuted).isEqualTo(12);
    }

    @Test
    void flywaySchemaHistory_shouldRecordAllMigrations() throws SQLException {
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
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank")) {

            int count = 0;
            while (rs.next()) {
                count++;
                boolean success = rs.getBoolean("success");
                assertThat(success).isTrue();
            }
            assertThat(count).isEqualTo(12);
        }
    }

    @Test
    void databaseSchema_shouldMatchMigrations() throws SQLException {
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
             Statement stmt = conn.createStatement()) {

            // Check that all expected tables exist
            ResultSet rs = stmt.executeQuery(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'public' " +
                "ORDER BY table_name"
            );

            StringBuilder tables = new StringBuilder();
            while (rs.next()) {
                tables.append(rs.getString("table_name")).append(", ");
            }

            // Verify expected tables exist
            assertThat(tables.toString()).contains("claims");
            assertThat(tables.toString()).contains("claim_parties");
            assertThat(tables.toString()).contains("claim_documents");
            assertThat(tables.toString()).contains("claim_status_history");
            assertThat(tables.toString()).contains("outbox_events");
            assertThat(tables.toString()).contains("processed_events");
            assertThat(tables.toString()).contains("idempotency_records");
            assertThat(tables.toString()).contains("claim_saga_orchestrations");
            assertThat(tables.toString()).contains("saga_processed_messages");
            assertThat(tables.toString()).contains("flyway_schema_history");
        }
    }

    @Test
    void claimsTable_shouldHaveExpectedColumns() throws SQLException {
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
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                "SELECT column_name, is_nullable, data_type " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'claims' " +
                "ORDER BY ordinal_position"
             )) {

            StringBuilder columns = new StringBuilder();
            while (rs.next()) {
                columns.append(rs.getString("column_name")).append(", ");
            }

            // Verify expected columns exist
            assertThat(columns.toString()).contains("id");
            assertThat(columns.toString()).contains("claim_number");
            assertThat(columns.toString()).contains("policy_id");
            assertThat(columns.toString()).contains("incident_type");
            assertThat(columns.toString()).contains("status");
            assertThat(columns.toString()).contains("estimated_amount_cents");
            assertThat(columns.toString()).contains("approved_amount_cents");
            assertThat(columns.toString()).contains("incident_date");
            assertThat(columns.toString()).contains("created_at");
            assertThat(columns.toString()).contains("updated_at");
            assertThat(columns.toString()).contains("deleted_at");
            assertThat(columns.toString()).contains("version");
            assertThat(columns.toString()).contains("locked_by");
            assertThat(columns.toString()).contains("locked_until");
        }
    }

    @Test
    void outboxEventsTable_shouldHaveTraceContextColumns() throws SQLException {
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
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                "SELECT column_name " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'outbox_events' " +
                "ORDER BY ordinal_position"
             )) {

            StringBuilder columns = new StringBuilder();
            while (rs.next()) {
                columns.append(rs.getString("column_name")).append(", ");
            }

            // Verify V7 migration columns exist
            assertThat(columns.toString()).contains("correlation_id");
            assertThat(columns.toString()).contains("trace_id");
            assertThat(columns.toString()).contains("span_id");
            assertThat(columns.toString()).contains("request_id");
        }
    }
}
