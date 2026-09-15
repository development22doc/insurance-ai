package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.policy_service.security.InternalRequestIdentity;
import com.claimassist.platform.policy_service.service.PolicyCoverageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/internal/v1/policies")
@RequiredArgsConstructor
@org.springframework.validation.annotation.Validated
public class PolicyInternalController {

    private final PolicyCoverageService policyCoverageService;
    private final InternalRequestIdentity internalRequestIdentity;

    @GetMapping("/{policyId}/coverage")
    public PolicyCoverageDto getPolicyCoverage(
            @PathVariable @jakarta.validation.constraints.Positive(message = "policyId must be positive") Long policyId,
            @RequestParam(value = "asOf", required = false) Instant asOf,
            @RequestHeader(value = "X-User-Id", required = false) String xUserIdHeader) {

        if (policyId == null || policyId <= 0) {
            throw new com.claimassist.platform.common_lib.error.BadRequestException("policyId must be a positive integer");
        }
        Long customerId = internalRequestIdentity.resolveCallingUserId(xUserIdHeader);
        return policyCoverageService.getCoverageForCustomer(policyId, customerId, asOf != null ? asOf : Instant.now());
    }
}
