package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.security.CallerType;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
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
public class PolicyInternalController {

    private final PolicyCoverageService policyCoverageService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/{policyId}/coverage")
    public PolicyCoverageDto getPolicyCoverage(
            @PathVariable Long policyId,
            @RequestParam(value = "asOf", required = false) Instant asOf,
            @RequestHeader(value = "X-User-Id", required = false) String xUserIdHeader) {

        Long customerId = resolveCustomerId(xUserIdHeader);
        return policyCoverageService.getCoverageForCustomer(policyId, customerId, asOf != null ? asOf : Instant.now());
    }

    private Long resolveCustomerId(String xUserIdHeader) {
        if (CurrentUserProvider.classify(currentUserProvider.getCurrentJwt()) == CallerType.USER) {
            return currentUserProvider.getCurrentUserId();
        }
        if (xUserIdHeader == null || xUserIdHeader.isBlank()) {
            throw new BadRequestException("Missing X-User-Id header for service caller");
        }
        try {
            return Long.valueOf(xUserIdHeader.trim());
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Invalid X-User-Id header");
        }
    }
}
