package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.policy_service.dto.PolicyContractDetailDto;
import com.claimassist.platform.policy_service.dto.PolicyContractSummaryDto;
import com.claimassist.platform.policy_service.dto.PolicyPeriodDto;
import com.claimassist.platform.policy_service.dto.RenewalInitiationResponse;
import com.claimassist.platform.policy_service.service.PolicyContractReadService;
import com.claimassist.platform.policy_service.service.PolicyLifecycleService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class CustomerPolicyController {

    private final PolicyContractReadService policyContractReadService;
    private final PolicyLifecycleService policyLifecycleService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/customers/me/policies")
    public ResponseEntity<List<PolicyContractSummaryDto>> getMyPolicies() {
        Long customerId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(policyContractReadService.getPoliciesForCustomer(customerId));
    }

    @GetMapping("/customers/me/policies/{policyId}")
    public ResponseEntity<PolicyContractDetailDto> getMyPolicy(@PathVariable @Positive Long policyId) {
        Long customerId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(policyContractReadService.getPolicyForCustomer(customerId, policyId));
    }

    @GetMapping("/customers/me/policies/{policyId}/periods")
    public ResponseEntity<List<PolicyPeriodDto>> getMyPolicyPeriods(@PathVariable @Positive Long policyId) {
        return ResponseEntity.ok(policyContractReadService.getPeriodsForPolicy(policyId));
    }

    @GetMapping("/customers/me/policies/{policyId}/history")
    public ResponseEntity<List<PolicyPeriodDto>> getMyPolicyHistory(@PathVariable @Positive Long policyId) {
        return ResponseEntity.ok(policyContractReadService.getPeriodsForPolicy(policyId));
    }

    @GetMapping("/customers/me/policies/{policyId}/current-period")
    public ResponseEntity<PolicyPeriodDto> getCurrentPolicyPeriod(@PathVariable @Positive Long policyId) {
        return ResponseEntity.ok(policyContractReadService.getCurrentPeriod(policyId));
    }

    @PostMapping("/customers/me/policies/{policyId}/cancel")
    public ResponseEntity<PolicyContractDetailDto> cancelPolicy(@PathVariable @Positive Long policyId) {
        Long customerId = currentUserProvider.getCurrentUserId();
        policyLifecycleService.cancelPolicy(policyId, customerId, null);
        return ResponseEntity.ok(policyContractReadService.getPolicyForCustomer(customerId, policyId));
    }

    @PostMapping("/customers/me/policies/{policyId}/renew")
    public ResponseEntity<RenewalInitiationResponse> renewPolicy(
            @PathVariable @Positive Long policyId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long customerId = currentUserProvider.getCurrentUserId();
        RenewalInitiationResponse response = policyLifecycleService.initiateRenewal(policyId, customerId, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
