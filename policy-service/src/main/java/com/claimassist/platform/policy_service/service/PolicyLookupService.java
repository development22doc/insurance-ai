package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.policy_service.dto.PolicySummaryDto;

import java.util.List;

public interface PolicyLookupService {
    PolicySummaryDto getPolicy(Long policyId, Long effectiveCustomerId);
    // Returns the policy without enforcing customer ownership - for ADMIN/OPERATIONS use
    PolicySummaryDto getPolicyForAdmin(Long policyId);
    List<PolicySummaryDto> getPoliciesForCustomer(Long customerId, Long effectiveCustomerId);
}
