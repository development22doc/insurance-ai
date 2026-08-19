package com.claimassist.platform.claims_service.controller;

import com.claimassist.platform.claims_service.entity.ClaimDocument;
import com.claimassist.platform.claims_service.repository.ClaimDocumentRepository;
import com.claimassist.platform.claims_service.service.query.ClaimQueryService;
import com.claimassist.platform.common_lib.dto.ClaimDocumentSummaryDto;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.enums.ClaimPermission;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4, Section 10 (IDOR) / Section 14 (authorization before cache): the
 * internal service-to-service endpoints (used by agent-service) re-validate the
 * caller's VIEW permission on EVERY request - including the status endpoint that
 * serves from the {@code @Cacheable} claim status cache. The authorization check
 * always runs BEFORE the cached read, so a caller who is not a party to the claim
 * is rejected with 404 (never leaking the claim's existence) and never reaches the
 * cache.
 */
class InternalClaimsControllerTest {

    private static final Long CLAIM_ID = 100L;
    private static final Long USER_ID = 7L;
    private static final Long OTHER_USER = 99L;

    private ClaimQueryService claimQueryService;
    private ClaimDocumentRepository claimDocumentRepository;
    private CurrentUserProvider currentUserProvider;
    private InternalClaimsController controller;

    @BeforeEach
    void setUp() {
        claimQueryService = mock(ClaimQueryService.class);
        claimDocumentRepository = mock(ClaimDocumentRepository.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        controller = new InternalClaimsController(claimQueryService, claimDocumentRepository, currentUserProvider);
    }

    private void stubCurrentUser(Long userId) {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    private void stubView(Long userId, boolean granted) {
        when(claimQueryService.hasPermissionForUser(CLAIM_ID, userId, ClaimPermission.VIEW))
                .thenReturn(granted);
    }

    @Test
    void getClaimStatusAuthorizedCallerHitsCacheBackedRead() {
        stubCurrentUser(USER_ID);
        stubView(USER_ID, true);
        ClaimStatusDto dto = new ClaimStatusDto(CLAIM_ID, 5L, "CLM-1", "SUBMITTED", "FIRE", 100L, null, List.of());
        when(claimQueryService.getClaimStatusWithHistory(CLAIM_ID)).thenReturn(dto);

        ClaimStatusDto result = controller.getClaimStatus(CLAIM_ID);

        assertThat(result).isSameAs(dto);
        verify(claimQueryService).getClaimStatusWithHistory(CLAIM_ID);
    }

    @Test
    void getClaimStatusUnauthorizedCallerIsRejected404BeforeAnyCacheRead() {
        stubCurrentUser(OTHER_USER);
        stubView(OTHER_USER, false);

        assertThatThrownBy(() -> controller.getClaimStatus(CLAIM_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        // Authorization fails -> the cached read must never be reached.
        verify(claimQueryService).hasPermissionForUser(CLAIM_ID, OTHER_USER, ClaimPermission.VIEW);
    }

    @Test
    void getClaimDocumentsAuthorizedCallerGetsMappedDocuments() {
        stubCurrentUser(USER_ID);
        stubView(USER_ID, true);
        ClaimDocument doc = ClaimDocument.builder().id(1L).docType("PHOTO").ocrStatus("PENDING").build();
        when(claimDocumentRepository.findByClaimId(CLAIM_ID)).thenReturn(List.of(doc));

        List<ClaimDocumentSummaryDto> result = controller.getClaimDocuments(CLAIM_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).docType()).isEqualTo("PHOTO");
        assertThat(result.get(0).ocrStatus()).isEqualTo("PENDING");
    }

    @Test
    void getClaimDocumentsUnauthorizedCallerIsRejected404() {
        stubCurrentUser(OTHER_USER);
        stubView(OTHER_USER, false);

        assertThatThrownBy(() -> controller.getClaimDocuments(CLAIM_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void checkPermissionDelegatesToQueryService() {
        when(claimQueryService.hasPermission(CLAIM_ID, ClaimPermission.UPDATE_STATUS)).thenReturn(true);
        assertThat(controller.checkPermission(CLAIM_ID, ClaimPermission.UPDATE_STATUS)).isTrue();
    }

    @Test
    void checkPermissionReturnsFalseWhenNotGranted() {
        when(claimQueryService.hasPermission(CLAIM_ID, ClaimPermission.UPDATE_STATUS)).thenReturn(false);
        assertThat(controller.checkPermission(CLAIM_ID, ClaimPermission.UPDATE_STATUS)).isFalse();
    }
}
