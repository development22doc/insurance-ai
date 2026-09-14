package com.claimassist.platform.claims_service.integration;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for PostgreSQL integration tests using Testcontainers.
 * Gated by {@code @Testcontainers(disabledWithoutDocker = true)}: when a Docker
 * environment is unavailable this reports as SKIPPED (never a false pass) and
 * runs for real in any Docker-enabled CI.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractPostgreSQLTest {

    @Container
    static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine")
    );
}
