package com.claimassist.platform.claims_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.claimassist.platform.claims_service.entity.Claim;
import com.claimassist.platform.claims_service.entity.ClaimStatusHistory;
import com.claimassist.platform.claims_service.repository.ClaimRepository;
import com.claimassist.platform.claims_service.repository.ClaimStatusHistoryRepository;
import com.claimassist.platform.claims_service.service.command.ClaimCommandService;
import com.claimassist.platform.claims_service.service.command.ClaimCommands.UpdateClaimStatusCommand;
import com.claimassist.platform.common_lib.enums.ClaimStatus;
import com.claimassist.platform.common_lib.error.ClaimStateTransitionException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;

import jakarta.annotation.Resource;

@SpringBootTest
@ActiveProfiles("testcontainers")
@Import(PostgresIntegrationTestConfig.class)
class ClaimPostgresTransactionIT {

    @Resource
    ClaimRepository claimRepository;

    @Resource
    ClaimStatusHistoryRepository claimStatusHistoryRepository;

    @Resource
    ClaimCommandService claimCommandService;

    @Resource
    TransactionTemplate transactionTemplate;

    @Test
    void notFound_findById_returnsEmpty() {
        assertThat(claimRepository.findById(9_999_999L)).isEmpty();
    }

    @Test
    void notFound_applyStatusChange_throwsResourceNotFoundException() {
        UpdateClaimStatusCommand command = new UpdateClaimStatusCommand(
                9_999_999L, ClaimStatus.UNDER_REVIEW.name(), "not found test", "tester");

        assertThatThrownBy(() -> claimCommandService.applyStatusChange(command))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Claim");
    }

    @Test
    void negative_invalidStatusTransition_doesNotChangeClaimOrHistory() {
        Claim claim = persistSubmittedClaim("CLM-NEG-TRANS-" + UUID.randomUUID());

        List<ClaimStatusHistory> historyBefore = claimStatusHistoryRepository.findAll().stream()
                .filter(h -> h.getClaimId().equals(claim.getId()))
                .toList();

        UpdateClaimStatusCommand command = new UpdateClaimStatusCommand(
                claim.getId(), ClaimStatus.APPROVED.name(), "invalid jump", "tester");

        assertThatThrownBy(() -> claimCommandService.applyStatusChange(command))
                .isInstanceOf(ClaimStateTransitionException.class);

        Claim reloaded = claimRepository.findById(claim.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);

        List<ClaimStatusHistory> historyAfter = claimStatusHistoryRepository.findAll().stream()
                .filter(h -> h.getClaimId().equals(claim.getId()))
                .toList();
        assertThat(historyAfter).hasSameSizeAs(historyBefore);
    }

    @Test
    void constraint_duplicateClaimNumber_throwsDataIntegrityViolationException() {
        String claimNumber = "CLM-DUP-NUM-" + UUID.randomUUID();

        Claim first = new Claim();
        first.setClaimNumber(claimNumber);
        first.setPolicyId(1L);
        first.setIncidentType("ACCIDENT");
        first.setStatus(ClaimStatus.SUBMITTED);
        first.setIncidentDate(Instant.now());
        claimRepository.saveAndFlush(first);

        Claim duplicate = new Claim();
        duplicate.setClaimNumber(claimNumber);
        duplicate.setPolicyId(2L);
        duplicate.setIncidentType("THEFT");
        duplicate.setStatus(ClaimStatus.SUBMITTED);
        duplicate.setIncidentDate(Instant.now());

        assertThatThrownBy(() -> claimRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(claimRepository.findByClaimNumber(claimNumber))
                .isPresent()
                .get()
                .extracting(Claim::getIncidentType)
                .isEqualTo("ACCIDENT");
    }

    @Test
    void constraint_notNullViolation_throwsDataIntegrityViolationException() {
        Claim claim = new Claim();
        claim.setPolicyId(1L);
        claim.setIncidentType("ACCIDENT");
        claim.setStatus(ClaimStatus.SUBMITTED);

        assertThatThrownBy(() -> claimRepository.saveAndFlush(claim))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void transaction_rollbackOnConstraintViolation_doesNotLeavePartialState() {
        String claimNumber = "CLM-TX-ROLLBACK-" + UUID.randomUUID();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            Claim first = new Claim();
            first.setClaimNumber(claimNumber);
            first.setPolicyId(1L);
            first.setIncidentType("ACCIDENT");
            first.setStatus(ClaimStatus.SUBMITTED);
            first.setIncidentDate(Instant.now());
            claimRepository.saveAndFlush(first);

            Claim duplicate = new Claim();
            duplicate.setClaimNumber(claimNumber);
            duplicate.setPolicyId(2L);
            duplicate.setIncidentType("THEFT");
            duplicate.setStatus(ClaimStatus.SUBMITTED);
            duplicate.setIncidentDate(Instant.now());
            claimRepository.saveAndFlush(duplicate);
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(claimRepository.findByClaimNumber(claimNumber)).isEmpty();
    }

    @Test
    void transaction_successfulStatusChange_commitsClaimAndHistory() {
        Claim claim = persistSubmittedClaim("CLM-TX-COMMIT-" + UUID.randomUUID());

        Claim updated = claimCommandService.applyStatusChange(new UpdateClaimStatusCommand(
                claim.getId(),
                ClaimStatus.UNDER_REVIEW.name(),
                "moved to review",
                "tester"));

        assertThat(updated.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);

        Claim reloaded = claimRepository.findById(claim.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);

        List<ClaimStatusHistory> history = claimStatusHistoryRepository.findAll().stream()
                .filter(h -> h.getClaimId().equals(claim.getId()))
                .toList();
        assertThat(history).isNotEmpty();
        assertThat(history).anyMatch(h ->
                ClaimStatus.SUBMITTED.name().equals(h.getFromStatus())
                        && ClaimStatus.UNDER_REVIEW.name().equals(h.getToStatus()));
    }

    @Test
    @Transactional
    void transaction_duplicateClaimNumberWithinTransactionalMethod_rollsBackEntireTestTransaction() {
        String claimNumber = "CLM-TX-METHOD-ROLLBACK-" + UUID.randomUUID();

        Claim first = new Claim();
        first.setClaimNumber(claimNumber);
        first.setPolicyId(1L);
        first.setIncidentType("ACCIDENT");
        first.setStatus(ClaimStatus.SUBMITTED);
        first.setIncidentDate(Instant.now());
        claimRepository.saveAndFlush(first);

        Claim duplicate = new Claim();
        duplicate.setClaimNumber(claimNumber);
        duplicate.setPolicyId(2L);
        duplicate.setIncidentType("THEFT");
        duplicate.setStatus(ClaimStatus.SUBMITTED);
        duplicate.setIncidentDate(Instant.now());

        assertThatThrownBy(() -> claimRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Claim persistSubmittedClaim(String claimNumber) {
        Claim claim = new Claim();
        claim.setClaimNumber(claimNumber);
        claim.setPolicyId(1L);
        claim.setIncidentType("ACCIDENT");
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setIncidentDate(Instant.now());
        return claimRepository.saveAndFlush(claim);
    }
}
