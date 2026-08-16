package com.claimassist.platform.customer_service.controller;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.customer_service.security.InternalRequestIdentity;
import com.claimassist.platform.customer_service.service.PolicyQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/policies")
@RequiredArgsConstructor
public class InternalCustomerController {

    private final PolicyQueryService policyQueryService;
    private final InternalRequestIdentity internalRequestIdentity;

    /**
     * F-2: supports both USER and trusted SERVICE callers. A USER token uses its
     * own authenticated userId; a trusted SERVICE token must supply an explicit
     * {@code X-User-Id} for the target customer. In both cases
     * PolicyQueryService enforces policy ownership (policy must belong to the
     * resolved user), so neither path bypasses authorization.
     */
    @GetMapping("/{policyId}/coverage")
    public PolicyCoverageDto getPolicyCoverage(
            @PathVariable Long policyId,
            @RequestHeader(value = "X-User-Id", required = false) String xUserIdHeader) {
        Long callingUserId = internalRequestIdentity.resolveCallingUserId(xUserIdHeader);
        return policyQueryService.getPolicyCoverage(policyId, callingUserId);
    }
}