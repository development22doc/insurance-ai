package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.policy_service.dto.PolicySummaryDto;

import java.util.List;

public interface PolicyLookupService {
    PolicySummaryDto getPolicy(Long policyId, Long effectiveCustomerId);
    List<PolicySummaryDto> getPoliciesForCustomer(Long customerId, Long effectiveCustomerId);
}
