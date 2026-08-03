package com.claimassist.platform.customer_service.controller;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.customer_service.service.PolicyQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping ("/internal/v1/policies")
@RequiredArgsConstructor
public class InternalCustomerController {

    private final PolicyQueryService policyQueryService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping ("/{policyId}/coverage")
    public PolicyCoverageDto getPolicyCoverage (@PathVariable Long policyId) {
        Long callingUserId = currentUserProvider.getCurrentUserId ();
        return policyQueryService.getPolicyCoverage (policyId, callingUserId);
    }
}
