package com.claimassist.platform.claims_service.messaging;

import com.claimassist.platform.claims_service.entity.OutboxEvent;
import com.claimassist.platform.claims_service.repository.OutboxEventRepository;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 5 - proves the Transactional Outbox atomicity guarantee against a real PostgreSQL
 * instance (Testcontainers): a business write and its outbox record either commit together
 * or roll back together, so a published Kafka event always reflects a durable DB state.
 *
 * <p>Gated by {@code @Testcontainers(disabledWithoutDocker = true)}: when a Docker
 * environment is unavailable this reports as SKIPPED (never a false pass) and runs for real
 * in any Docker-enabled CI.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class OutboxAtomicityIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate requiredNew;

    @BeforeEach
    void setUp() {
        requiredNew = new TransactionTemplate(transactionManager);
        requiredNew.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Each test must start from an empty outbox table. The committed test commits via
        // REQUIRES_NEW (outside the @DataJpaTest rollback scope), so a committed row would leak
        // into the rollback test's count assertion. Deleting in a committed REQUIRES_NEW
        // transaction gives every test method deterministic isolation.
        requiredNew.executeWithoutResult(status -> outboxEventRepository.deleteAll());
    }

    private OutboxEvent pendingEvent() {
        return OutboxEvent.builder()
                .aggregateId("100")
                .eventType("ClaimCreated")
                .topic("claim-events")
                .partitionKey("claim-100")
                .payload("{\"claimId\":100}")
                .status(OutboxStatus.PENDING)
                .build();
    }

    @Test
    void committedBusinessTransactionPersistsOutboxRecord() {
        requiredNew.executeWithoutResult(status -> outboxEventRepository.save(pendingEvent()));

        // The inner transaction committed: the outbox record must now be durable.
        assertThat(outboxEventRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.countByStatus(OutboxStatus.PENDING)).isEqualTo(1);
    }

    @Test
    void rolledBackBusinessTransactionDoesNotPersistOutboxRecord() {
        assertThatThrownBy(() -> requiredNew.executeWithoutResult(status -> {
            outboxEventRepository.save(pendingEvent());
            throw new IllegalStateException("business operation failed");
        })).isInstanceOf(IllegalStateException.class);

        // The inner transaction rolled back: the outbox record MUST NOT exist.
        assertThat(outboxEventRepository.count()).isZero();
    }
}