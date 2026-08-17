package com.claimassist.platform.claims_service.repository;

import com.claimassist.platform.claims_service.entity.Claim;
import com.claimassist.platform.claims_service.entity.ClaimParty;
import com.claimassist.platform.claims_service.entity.ClaimPartyId;
import com.claimassist.platform.claims_service.entity.ClaimStatusHistory;
import com.claimassist.platform.common_lib.enums.ClaimRole;
import com.claimassist.platform.common_lib.enums.ClaimStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4 - PostgreSQL integration tests against a real PostgreSQL instance
 * (Testcontainers) so Flyway migrations, indexes, the ownership queries and
 * optimistic locking are exercised against the actual production database
 * engine - not an emulation.
 *
 * <p>Gated by {@code @Testcontainers(disabledWithoutDocker = true)}: when a
 * Docker environment is unavailable (e.g. a degraded local Docker Desktop
 * engine, as observed here where the daemon's {@code /info} call returns an
 * empty payload), these tests report as SKIPPED with a clear log message
 * rather than a false pass. They run for real in any environment where Docker
 * is available (e.g. CI).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ClaimRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimPartyRepository claimPartyRepository;

    @Autowired
    private ClaimStatusHistoryRepository claimStatusHistoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);

    @BeforeEach
    void seedClaimAndParty() {
        Claim claim = newClaim(1L);
        Claim saved = claimRepository.saveAndFlush(claim);
        claimPartyRepository.saveAndFlush(ClaimParty.builder()
                .id(new ClaimPartyId(saved.getId(), 1L))
                .claim(saved)
                .claimRole(ClaimRole.POLICYHOLDER)
                .build());
    }

    private Claim newClaim(Long userId) {
        return Claim.builder()
                .claimNumber("IT-" + SEQUENCE.incrementAndGet())
                .policyId(userId)
                .incidentType("FIRE")
                .incidentDate(Instant.now().minusSeconds(60))
                .status(ClaimStatus.SUBMITTED)
                .estimatedAmountCents(1000L)
                .build();
    }

    private Long claimId() {
        return claimRepository.findAll().get(0).getId();
    }

    @Test
    void flywayMigrationsAreApplied() {
        Integer claimIdColumnCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'claims' AND column_name = 'version'",
                Integer.class);
        // V9/V10 add the optimistic-lock version column; if Flyway ran, it exists.
        assertThat(claimIdColumnCount).isEqualTo(1);
    }

    @Test
    void phase4IndexesAreCreatedByV11Migration() {
        int historyIndex = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE tablename='claim_status_history' AND indexname='idx_claim_status_history_claim_id'",
                Integer.class);
        int documentIndex = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE tablename='claim_documents' AND indexname='idx_claim_documents_claim_id'",
                Integer.class);
        assertThat(historyIndex).isEqualTo(1);
        assertThat(documentIndex).isEqualTo(1);
    }

    @Test
    void findAllAccessibleByUserReturnsOnlyClaimsTheUserIsAPartyTo() {
        List<ClaimSummaryRow> forOwner = claimRepository.findAllAccessibleByUser(1L);
        assertThat(forOwner).hasSize(1);
        assertThat(forOwner.get(0).role()).isEqualTo(ClaimRole.POLICYHOLDER);

        List<ClaimSummaryRow> forStranger = claimRepository.findAllAccessibleByUser(999L);
        assertThat(forStranger).isEmpty();
    }

    @Test
    void findAccessibleClaimWithRoleReturnsClaimAndRoleForPartyOnly() {
        Long id = claimId();
        var forOwner = claimRepository.findAccessibleClaimWithRoleByClaimIdAndUserId(id, 1L);
        assertThat(forOwner).isPresent();
        assertThat(forOwner.get().getClaim().getId()).isEqualTo(id);
        assertThat(forOwner.get().getRole()).isEqualTo(ClaimRole.POLICYHOLDER);

        // IDOR: a caller who is not a party gets an empty result from the join.
        var forStranger = claimRepository.findAccessibleClaimWithRoleByClaimIdAndUserId(id, 999L);
        assertThat(forStranger).isEmpty();
    }

    @Test
    void statusHistoryQueryIsBackedByV11IndexAndReturnsOrderedHistory() {
        Long id = claimId();
        claimStatusHistoryRepository.save(ClaimStatusHistory.builder()
                .claimId(id).fromStatus("NONE").toStatus("SUBMITTED").changedBy("1").build());
        claimStatusHistoryRepository.save(ClaimStatusHistory.builder()
                .claimId(id).fromStatus("SUBMITTED").toStatus("UNDER_REVIEW").changedBy("1")
                .changedAt(Instant.now().plusSeconds(10)).build());
        claimStatusHistoryRepository.flush();

        List<ClaimStatusHistory> history = claimStatusHistoryRepository.findByClaimIdOrderByChangedAtAsc(id);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getToStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void staleEntityUpdateFailsWithOptimisticLockingConflict() {
        Claim claim = newClaim(1L);
        Claim saved = claimRepository.saveAndFlush(claim);
        Long id = saved.getId();

        // Concurrent writer advances the row's @Version in another transaction.
        jdbcTemplate.update(
                "UPDATE claims SET status = ?, version = version + 1 WHERE id = ?",
                ClaimStatus.UNDER_REVIEW.name(), id);

        // The persistence context still holds the stale version (1). Saving the
        // stale aggregate must fail, NOT silently overwrite the newer status.
        saved.setStatus(ClaimStatus.DENIED);
        assertThatThrownBy(() -> claimRepository.saveAndFlush(saved))
                .isInstanceOf(OptimisticLockingFailureException.class);

        // The concurrent write is intact (not overwritten by the failed update).
        String statusInDb = jdbcTemplate.queryForObject(
                "SELECT status FROM claims WHERE id = ?", String.class, id);
        assertThat(statusInDb).isEqualTo(ClaimStatus.UNDER_REVIEW.name());
    }
}
