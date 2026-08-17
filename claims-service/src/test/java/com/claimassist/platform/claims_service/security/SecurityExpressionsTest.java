package com.claimassist.platform.claims_service.security;

import com.claimassist.platform.claims_service.repository.ClaimPartyRepository;
import com.claimassist.platform.common_lib.enums.ClaimPermission;
import com.claimassist.platform.common_lib.enums.ClaimRole;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 4, Section 9/10/14: the per-claim RBAC boundary. Every ClaimRole's
 * permission matrix is evaluated exactly once per (claimId, userId, permission)
 * and a caller who is NOT a party to the claim is never granted anything -
 * fail-closed, never fail-open on "no role found".
 */
class SecurityExpressionsTest {

    private static final Long CLAIM_ID = 10L;

    private ClaimPartyRepository claimPartyRepository;
    private CurrentUserProvider currentUserProvider;
    private SecurityExpressions security;

    @BeforeEach
    void setUp() {
        claimPartyRepository = mock(ClaimPartyRepository.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        security = new SecurityExpressions(currentUserProvider, claimPartyRepository);
    }

    private void stubRole(Long userId, ClaimRole role) {
        when(claimPartyRepository.findRoleByClaimIdAndUserId(CLAIM_ID, userId))
                .thenReturn(Optional.ofNullable(role));
    }

    @Test
    void policyholderCanViewButCannotUpdateStatus() {
        stubRole(1L, ClaimRole.POLICYHOLDER);
        assertThat(security.hasPermissionForUser(CLAIM_ID, 1L, ClaimPermission.VIEW)).isTrue();
        assertThat(security.hasPermissionForUser(CLAIM_ID, 1L, ClaimPermission.VIEW_PARTIES)).isTrue();
        assertThat(security.hasPermissionForUser(CLAIM_ID, 1L, ClaimPermission.SUBMIT_DOCUMENTS)).isTrue();
        assertThat(security.hasPermissionForUser(CLAIM_ID, 1L, ClaimPermission.UPDATE_STATUS)).isFalse();
        assertThat(security.hasPermissionForUser(CLAIM_ID, 1L, ClaimPermission.MANAGE_PARTIES)).isFalse();
    }

    @Test
    void adjusterHasUpdateStatusAndManageParties() {
        stubRole(2L, ClaimRole.ADJUSTER);
        assertThat(security.hasPermissionForUser(CLAIM_ID, 2L, ClaimPermission.UPDATE_STATUS)).isTrue();
        assertThat(security.hasPermissionForUser(CLAIM_ID, 2L, ClaimPermission.MANAGE_PARTIES)).isTrue();
        assertThat(security.hasPermissionForUser(CLAIM_ID, 2L, ClaimPermission.VIEW)).isTrue();
    }

    @Test
    void auditorCanViewAuditTrailButCannotChangeStatus() {
        stubRole(3L, ClaimRole.AUDITOR);
        assertThat(security.hasPermissionForUser(CLAIM_ID, 3L, ClaimPermission.VIEW)).isTrue();
        assertThat(security.hasPermissionForUser(CLAIM_ID, 3L, ClaimPermission.VIEW_AUDIT_TRAIL)).isTrue();
        assertThat(security.hasPermissionForUser(CLAIM_ID, 3L, ClaimPermission.UPDATE_STATUS)).isFalse();
    }

    @Test
    void userWhoIsNotAPartyIsFailClosedAndGrantedNothing() {
        // No role row at all -> empty Optional -> false, never a blanket allow.
        when(claimPartyRepository.findRoleByClaimIdAndUserId(CLAIM_ID, 99L))
                .thenReturn(Optional.empty());
        assertThat(security.hasPermissionForUser(CLAIM_ID, 99L, ClaimPermission.VIEW)).isFalse();
        assertThat(security.hasPermissionForUser(CLAIM_ID, 99L, ClaimPermission.UPDATE_STATUS)).isFalse();
        assertThat(security.hasPermissionForUser(CLAIM_ID, 99L, ClaimPermission.SUBMIT_DOCUMENTS)).isFalse();
    }

    @Test
    void canViewDelegatesToViewPermissionForCurrentUser() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);
        stubRole(1L, ClaimRole.POLICYHOLDER);
        assertThat(security.canView(CLAIM_ID)).isTrue();

        when(currentUserProvider.getCurrentUserId()).thenReturn(99L);
        when(claimPartyRepository.findRoleByClaimIdAndUserId(CLAIM_ID, 99L))
                .thenReturn(Optional.empty());
        assertThat(security.canView(CLAIM_ID)).isFalse();
    }

    @Test
    void canUpdateStatusUsesUpdateStatusPermission() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(2L);
        stubRole(2L, ClaimRole.ADJUSTER);
        assertThat(security.canUpdateStatus(CLAIM_ID)).isTrue();
    }
}
