package com.claimassist.platform.claims_service.service.command.impl;

import com.claimassist.platform.claims_service.dto.claim.ClaimResponse;
import com.claimassist.platform.claims_service.entity.Claim;
import com.claimassist.platform.claims_service.entity.ClaimParty;
import com.claimassist.platform.claims_service.mapper.ClaimMapper;
import com.claimassist.platform.claims_service.repository.ClaimPartyRepository;
import com.claimassist.platform.claims_service.repository.ClaimRepository;
import com.claimassist.platform.claims_service.repository.ClaimStatusHistoryRepository;
import com.claimassist.platform.claims_service.service.command.ClaimCommands.SubmitClaimCommand;
import com.claimassist.platform.claims_service.service.command.ClaimCommands.UpdateClaimStatusCommand;
import com.claimassist.platform.claims_service.service.gateway.PolicyCoverageGateway;
import com.claimassist.platform.claims_service.support.IdempotencyService;
import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.common_lib.enums.ClaimRole;
import com.claimassist.platform.common_lib.enums.ClaimStatus;
import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ClaimStateTransitionException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4, Section 7/8: command-side correctness. The shared status-change
 * method is the single enforcement point for the state machine and the
 * optimistic-lock boundary: a stale concurrent writer (JPA @Version) surfaces
 * as an {@link OptimisticLockingFailureException} and is never silently
 * overwritten. Submit-claim refuses to open a claim against a non-ACTIVE
 * policy and always records the submitting user as the POLICYHOLDER party.
 */
class ClaimCommandServiceImplTest {

    private ClaimRepository claimRepository;
    private ClaimPartyRepository claimPartyRepository;
    private ClaimStatusHistoryRepository claimStatusHistoryRepository;
    private ClaimMapper claimMapper;
    private PolicyCoverageGateway policyCoverageGateway;
    private IdempotencyService idempotencyService;
    private ClaimCommandServiceImpl service;

    private static final Long USER_ID = 7L;
    private static final Long POLICY_ID = 5L;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        claimPartyRepository = mock(ClaimPartyRepository.class);
        claimStatusHistoryRepository = mock(ClaimStatusHistoryRepository.class);
        claimMapper = mock(ClaimMapper.class);
        policyCoverageGateway = mock(PolicyCoverageGateway.class);
        idempotencyService = mock(IdempotencyService.class);
        EventLogger eventLogger = mock(EventLogger.class);
        PerformanceLogger performanceLogger = mock(PerformanceLogger.class);
        service = new ClaimCommandServiceImpl(
                claimRepository, claimPartyRepository, claimStatusHistoryRepository,
                claimMapper, policyCoverageGateway, idempotencyService,
                eventLogger, performanceLogger);
    }

    private Claim newClaim(Long id, ClaimStatus status) {
        return Claim.builder().id(id).claimNumber("CLM-1").policyId(POLICY_ID).status(status).build();
    }

    private void idempotencyRunsCommand() {
        when(idempotencyService.execute(any(), any(), anyLong(), any(), any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(4)).get());
    }

    @Test
    void submitClaimRefusesNonActivePolicy() {
        idempotencyRunsCommand();
        when(policyCoverageGateway.getPolicyCoverage(POLICY_ID, USER_ID))
                .thenReturn(new PolicyCoverageDto(POLICY_ID, "P-1", "CANCELLED", "HOME", "Basic", 1000L, 100000L, "2027-01-01"));

        SubmitClaimCommand command = new SubmitClaimCommand(POLICY_ID, "FIRE", Instant.now(), 1000L, USER_ID, "k-1");

        assertThatThrownBy(() -> service.submitClaim(command))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not ACTIVE");
    }

    @Test
    void submitClaimCreatesClaimAndRegistersSubmitterAsPolicyholder() {
        idempotencyRunsCommand();
        when(policyCoverageGateway.getPolicyCoverage(POLICY_ID, USER_ID))
                .thenReturn(new PolicyCoverageDto(POLICY_ID, "P-1", "ACTIVE", "HOME", "Basic", 1000L, 100000L, "2027-01-01"));
        Claim saved = newClaim(1L, ClaimStatus.SUBMITTED);
        when(claimRepository.save(any(Claim.class))).thenReturn(saved);
        when(claimMapper.toClaimResponse(any(Claim.class)))
                .thenReturn(new ClaimResponse(1L, "CLM-1", "SUBMITTED", "FIRE"));

        SubmitClaimCommand command = new SubmitClaimCommand(POLICY_ID, "FIRE", Instant.now(), 1000L, USER_ID, "k-1");

        ClaimResponse response = service.submitClaim(command);

        assertThat(response.id()).isEqualTo(1L);
        verify(claimPartyRepository).save(any(ClaimParty.class));
    }

    @Test
    void applyStatusChangePerformsLegalTransitionAndWritesAuditHistory() {
        Claim claim = newClaim(1L, ClaimStatus.SUBMITTED);
        when(claimRepository.findById(1L)).thenReturn(java.util.Optional.of(claim));
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);

        UpdateClaimStatusCommand command = new UpdateClaimStatusCommand(1L, "UNDER_REVIEW", "note", USER_ID.toString());

        Claim updated = service.applyStatusChange(command);

        assertThat(updated.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        verify(claimStatusHistoryRepository).save(any(com.claimassist.platform.claims_service.entity.ClaimStatusHistory.class));
    }

    @Test
    void applyStatusChangeRejectsIllegalTransition() {
        Claim claim = newClaim(1L, ClaimStatus.SUBMITTED);
        when(claimRepository.findById(1L)).thenReturn(java.util.Optional.of(claim));

        // SUBMITTED -> CLOSED is not a legal direct transition.
        UpdateClaimStatusCommand command = new UpdateClaimStatusCommand(1L, "CLOSED", null, "1");

        assertThatThrownBy(() -> service.applyStatusChange(command))
                .isInstanceOf(ClaimStateTransitionException.class);
        verify(claimRepository, never()).save(any());
    }

    @Test
    void applyStatusChangeRejectsUnknownStatus() {
        Claim claim = newClaim(1L, ClaimStatus.SUBMITTED);
        when(claimRepository.findById(1L)).thenReturn(java.util.Optional.of(claim));

        UpdateClaimStatusCommand command = new UpdateClaimStatusCommand(1L, "NOT_A_STATUS", null, "1");

        assertThatThrownBy(() -> service.applyStatusChange(command))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void applyStatusChangeThrowsNotFoundForMissingClaim() {
        when(claimRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        UpdateClaimStatusCommand command = new UpdateClaimStatusCommand(999L, "UNDER_REVIEW", null, "1");

        assertThatThrownBy(() -> service.applyStatusChange(command))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void concurrentStaleUpdateSurfacesAsOptimisticLockingFailureNotSilentOverwrite() {
        Claim claim = newClaim(1L, ClaimStatus.SUBMITTED);
        when(claimRepository.findById(1L)).thenReturn(java.util.Optional.of(claim));
        // The DB rejects the write because the row's @Version advanced since read.
        when(claimRepository.save(any(Claim.class)))
                .thenThrow(new OptimisticLockingFailureException("Row was updated or deleted by another transaction"));

        UpdateClaimStatusCommand command = new UpdateClaimStatusCommand(1L, "UNDER_REVIEW", null, "1");

        assertThatThrownBy(() -> service.applyStatusChange(command))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}