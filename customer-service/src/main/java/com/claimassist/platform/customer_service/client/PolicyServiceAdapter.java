package com.claimassist.platform.customer_service.client;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.customer_service.dto.PolicySummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Thin read-only adapter for Policy Service lookups.
 * Customer Service no longer owns a runtime policy domain or lifecycle.
 */
@Component
@RequiredArgsConstructor
public class PolicyServiceAdapter {

    private final PolicyServiceClient policyServiceClient;

    public PolicySummaryDto getPolicyById(Long policyId, Long actingUserId) {
        return policyServiceClient.getPolicyById(policyId, actingUserId);
    }

    public List<PolicySummaryDto> getPoliciesForCustomer(Long customerId, Long actingUserId) {
        return policyServiceClient.getPoliciesForCustomer(customerId, actingUserId);
    }

    public PolicyCoverageDto getPolicyCoverage(Long policyId, Long actingUserId) {
        return policyServiceClient.getPolicyCoverage(policyId, actingUserId);
    }
}