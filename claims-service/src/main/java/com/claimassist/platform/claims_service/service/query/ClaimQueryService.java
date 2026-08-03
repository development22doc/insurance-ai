package com.claimassist.platform.claims_service.service.query;

import com.claimassist.platform.claims_service.dto.claim.ClaimSummaryResponse;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.enums.ClaimPermission;

import java.util.List;

public interface ClaimQueryService {

    List<ClaimSummaryResponse> getMyClaims();

    ClaimSummaryResponse getClaimById(Long claimId);

    /** Used by both the internal endpoint and agent-service's get_claim_status tool. */
    ClaimStatusDto getClaimStatusWithHistory(Long claimId);

    boolean hasPermission(Long claimId, ClaimPermission permission);

    boolean hasPermissionForUser(Long claimId, Long userId, ClaimPermission permission);
}
