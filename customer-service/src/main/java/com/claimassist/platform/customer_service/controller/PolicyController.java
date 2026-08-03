package com.claimassist.platform.customer_service.controller;

import com.claimassist.platform.customer_service.dto.policy.PolicyResponse;
import com.claimassist.platform.customer_service.service.PolicyQueryService;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Kept intentionally simple (no CQRS command/query split) - this is a
 * read-mostly, low-complexity resource. See INSURANCE_AI_PLATFORM.md for
 * where this deliberately stops mirroring claims-service's fuller CQRS
 * treatment: the added structure isn't worth it for a single GET endpoint.
 */
@RestController
@RequestMapping("/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyQueryService policyQueryService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<List<PolicyResponse>> getMyPolicies() {
        Long customerId = currentUserProvider.getCurrentUserId();
        List<PolicyResponse> policies = policyQueryService.getMyPolicies(customerId);
        return ResponseEntity.ok(policies);
    }
}
