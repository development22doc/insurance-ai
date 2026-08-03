package com.claimassist.platform.claims_service.controller;

import com.claimassist.platform.claims_service.repository.ClaimDocumentRepository;
import com.claimassist.platform.claims_service.service.query.ClaimQueryService;
import com.claimassist.platform.common_lib.dto.ClaimDocumentSummaryDto;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.enums.ClaimPermission;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping ("/internal/v1/claims")
@RequiredArgsConstructor
public class InternalClaimsController {

    private final ClaimQueryService claimQueryService;
    private final ClaimDocumentRepository claimDocumentRepository;
    private final com.claimassist.platform.common_lib.security.CurrentUserProvider currentUserProvider;

    @GetMapping ("/{claimId}/status")
    public ClaimStatusDto getClaimStatus (@PathVariable Long claimId) {
        requireView (claimId);
        return claimQueryService.getClaimStatusWithHistory (claimId);
    }

    @GetMapping ("/{claimId}/documents")
    public List<ClaimDocumentSummaryDto> getClaimDocuments (@PathVariable Long claimId) {
        requireView (claimId);
        return claimDocumentRepository.findByClaimId (claimId).stream ()
                .map (d -> new ClaimDocumentSummaryDto (d.getId (), d.getDocType (), d.getOcrStatus (), d.getExtractedText (), d.getFraudSignalScore ()))
                .toList ();
    }

    @GetMapping ("/{claimId}/permissions/check")
    public boolean checkPermission (@PathVariable Long claimId, @RequestParam ClaimPermission permission) {
        return claimQueryService.hasPermission (claimId, permission);
    }

    private void requireView (Long claimId) {
        Long userId = currentUserProvider.getCurrentUserId ();
        if (!claimQueryService.hasPermissionForUser (claimId, userId, ClaimPermission.VIEW)) {
            // 404, not 403 - don't confirm existence of a claim you're not a party to.
            throw new ResourceNotFoundException ("Claim", claimId.toString ());
        }
    }
}
