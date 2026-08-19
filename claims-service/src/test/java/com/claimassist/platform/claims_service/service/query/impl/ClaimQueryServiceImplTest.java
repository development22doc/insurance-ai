package com.claimassist.platform.claims_service.service.query.impl;

import com.claimassist.platform.claims_service.dto.claim.ClaimSummaryResponse;
import com.claimassist.platform.claims_service.entity.Claim;
import com.claimassist.platform.claims_service.entity.ClaimStatusHistory;
import com.claimassist.platform.claims_service.mapper.ClaimMapper;
import com.claimassist.platform.claims_service.repository.ClaimPartyRepository;
import com.claimassist.platform.claims_service.repository.ClaimRepository;
import com.claimassist.platform.claims_service.repository.ClaimStatusHistoryRepository;
import com.claimassist.platform.claims_service.repository.ClaimSummaryRow;
import com.claimassist.platform.claims_service.security.SecurityExpressions;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.enums.ClaimRole;
import com.claimassist.platform.common_lib.enums.ClaimStatus;
import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4, Section 2/3/10: the query service must load only what it needs,
 * enforce ownership at the data layer, and map access strictly through the
 * caller's party membership. A non-party caller must NOT be able to fetch a
 * claim (IDOR): the underlying ownership query returning empty surfaces as a
 * ResourceNotFoundException and no claim data is returned.
 */
class ClaimQueryServiceImplTest {

    private ClaimRepository claimRepository;
    private ClaimPartyRepository claimPartyRepository;
    private ClaimStatusHistoryRepository claimStatusHistoryRepository;
    private ClaimMapper claimMapper;
    private CurrentUserProvider currentUserProvider;
    private SecurityExpressions securityExpressions;
    private ClaimQueryServiceImpl service;

    private static final Long USER_ID = 7L;
    private static final Long CLAIM_ID = 100L;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        claimPartyRepository = mock(ClaimPartyRepository.class);
        claimStatusHistoryRepository = mock(ClaimStatusHistoryRepository.class);
        claimMapper = mock(ClaimMapper.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        securityExpressions = mock(SecurityExpressions.class);
        PerformanceLogger performanceLogger = mock(PerformanceLogger.class);
        service = new ClaimQueryServiceImpl(
                claimRepository, claimPartyRepository, claimStatusHistoryRepository,
                claimMapper, currentUserProvider, securityExpressions, performanceLogger);
    }

    @Test
    void getMyClaimsMapsOnlyAccessibleRowsForCurrentUser() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        ClaimSummaryRow row = new ClaimSummaryRow(
                CLAIM_ID, "CLM-1", 5L, "FIRE", ClaimStatus.SUBMITTED,
                1000L, null, Instant.now(), Instant.now(), ClaimRole.POLICYHOLDER);
        when(claimRepository.findAllAccessibleByUser(USER_ID)).thenReturn(List.of(row));

        List<ClaimSummaryResponse> result = service.getMyClaims();

        assertThat(result).hasSize(1);
        ClaimSummaryResponse r = result.get(0);
        assertThat(r.id()).isEqualTo(CLAIM_ID);
        assertThat(r.role()).isEqualTo(ClaimRole.POLICYHOLDER.name());
        assertThat(r.status()).isEqualTo(ClaimStatus.SUBMITTED.name());
        verify(claimRepository).findAllAccessibleByUser(USER_ID);
    }

    @Test
    void getClaimByIdReturnsClaimAndRoleInOneOwnershipGuardedLookup() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        Claim claim = Claim.builder().id(CLAIM_ID).claimNumber("CLM-1").status(ClaimStatus.SUBMITTED).build();
        ClaimRepository.ClaimWithRoleProjection projection = mock(ClaimRepository.ClaimWithRoleProjection.class);
        when(projection.getClaim()).thenReturn(claim);
        when(projection.getRole()).thenReturn(ClaimRole.ADJUSTER);
        when(claimRepository.findAccessibleClaimWithRoleByClaimIdAndUserId(CLAIM_ID, USER_ID))
                .thenReturn(Optional.of(projection));
        when(claimMapper.toClaimSummaryResponse(claim, ClaimRole.ADJUSTER))
                .thenReturn(new ClaimSummaryResponse(CLAIM_ID, "CLM-1", 5L, "FIRE", "SUBMITTED",
                        1000L, null, "ADJUSTER", Instant.now(), Instant.now()));

        ClaimSummaryResponse result = service.getClaimById(CLAIM_ID);

        assertThat(result.id()).isEqualTo(CLAIM_ID);
        assertThat(result.role()).isEqualTo("ADJUSTER");
        verify(claimRepository).findAccessibleClaimWithRoleByClaimIdAndUserId(CLAIM_ID, USER_ID);
    }

    @Test
    void getClaimByIdThrowsNotFoundWhenCallerIsNotAParty() {
        // IDOR: the ownership query is empty for a non-party caller -> 404, no data.
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(claimRepository.findAccessibleClaimWithRoleByClaimIdAndUserId(CLAIM_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getClaimById(CLAIM_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getClaimByIdThrowsNotFoundWhenClaimDoesNotExist() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(claimRepository.findAccessibleClaimWithRoleByClaimIdAndUserId(CLAIM_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getClaimById(CLAIM_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void toClaimSummaryRejectsCallerWhoIsNotAParty() {
        when(claimPartyRepository.findRoleByClaimIdAndUserId(CLAIM_ID, USER_ID))
                .thenReturn(Optional.empty());
        Claim claim = Claim.builder().id(CLAIM_ID).claimNumber("CLM-1").build();

        assertThatThrownBy(() -> service.toClaimSummary(claim, USER_ID))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getClaimStatusWithHistoryBuildsHistoryEntries() {
        Claim claim = Claim.builder().id(CLAIM_ID).claimNumber("CLM-1")
                .policyId(5L).status(ClaimStatus.UNDER_REVIEW).incidentType("FIRE").build();
        when(claimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));
        ClaimStatusHistory history = ClaimStatusHistory.builder()
                .claimId(CLAIM_ID).fromStatus("SUBMITTED").toStatus("UNDER_REVIEW")
                .changedBy("agent-1").changedAt(Instant.parse("2026-01-01T00:00:00Z")).build();
        when(claimStatusHistoryRepository.findByClaimIdOrderByChangedAtAsc(CLAIM_ID))
                .thenReturn(List.of(history));

        ClaimStatusDto dto = service.getClaimStatusWithHistory(CLAIM_ID);

        assertThat(dto.status()).isEqualTo("UNDER_REVIEW");
        assertThat(dto.history()).hasSize(1);
        assertThat(dto.history().get(0).changedBy()).isEqualTo("agent-1");
    }

    @Test
    void hasPermissionDelegatesToSecurityExpressions() {
        when(securityExpressions.hasPermissionForUser(eq(CLAIM_ID), eq(USER_ID), any()))
                .thenReturn(true);
        assertThat(service.hasPermissionForUser(CLAIM_ID, USER_ID, com.claimassist.platform.common_lib.enums.ClaimPermission.VIEW)).isTrue();
    }
}
