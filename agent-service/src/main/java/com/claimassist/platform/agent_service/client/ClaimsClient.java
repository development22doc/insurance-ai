package com.claimassist.platform.agent_service.client;

import com.claimassist.platform.common_lib.dto.ClaimDocumentSummaryDto;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.enums.ClaimPermission;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "claims-service", url = "${CLAIMS_SERVICE_URI:}")
public interface ClaimsClient {

    @GetMapping("/internal/v1/claims/{claimId}/status")
    ClaimStatusDto getClaimStatus(@PathVariable Long claimId);

    @GetMapping("/internal/v1/claims/{claimId}/documents")
    List<ClaimDocumentSummaryDto> getClaimDocuments(@PathVariable Long claimId);

    @GetMapping("/internal/v1/claims/{claimId}/permissions/check")
    boolean checkPermission(@PathVariable Long claimId, @RequestParam ClaimPermission permission);
}
