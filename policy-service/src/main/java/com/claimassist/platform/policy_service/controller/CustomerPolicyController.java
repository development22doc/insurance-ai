package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.policy_service.dto.CustomerPolicyDetailDto;
import com.claimassist.platform.policy_service.dto.CustomerPolicySummaryDto;
import com.claimassist.platform.policy_service.dto.PolicyPeriodDto;
import com.claimassist.platform.policy_service.service.CustomerPolicyReadService;
import com.claimassist.platform.policy_service.service.PolicyContractReadService;
import com.claimassist.platform.policy_service.service.PolicyLifecycleService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class CustomerPolicyController {

    private final CustomerPolicyReadService customerPolicyReadService;
    private final PolicyContractReadService policyContractReadService;
    private final PolicyLifecycleService policyLifecycleService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/customers/me/policies")
    public ResponseEntity<List<CustomerPolicySummaryDto>> getMyPolicies() {
        Long customerId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(customerPolicyReadService.getPoliciesForCustomer(customerId));
    }

    @GetMapping("/customers/me/policies/{policyId}")
    public ResponseEntity<CustomerPolicyDetailDto> getMyPolicy(@PathVariable @Positive Long policyId) {
        Long customerId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(customerPolicyReadService.getPolicyForCustomer(customerId, policyId));
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
    public ResponseEntity<CustomerPolicyDetailDto> cancelPolicy(@PathVariable @Positive Long policyId) {
        Long customerId = currentUserProvider.getCurrentUserId();
        policyLifecycleService.cancelPolicy(policyId, customerId, null);
        return ResponseEntity.ok(customerPolicyReadService.getPolicyForCustomer(customerId, policyId));
    }
}
