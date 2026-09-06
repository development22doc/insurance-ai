package com.claimassist.platform.policy_service.service.impl;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.dto.PolicyCoverageProjection;
import com.claimassist.platform.policy_service.repository.PolicyRepository;
import com.claimassist.platform.policy_service.service.PolicyCoverageQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PolicyCoverageQueryServiceImpl implements PolicyCoverageQueryService {

    private final PolicyRepository policyRepository;

    @Override
    @Transactional(readOnly = true)
    public PolicyCoverageDto getPolicyCoverage(Long policyId, String xUserIdHeader) {
        Long callingUserId = null;
        if (xUserIdHeader != null) {
            try {
                callingUserId = Long.valueOf(xUserIdHeader);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("X-User-Id must be a numeric user id");
            }
        }

        PolicyCoverageProjection p = policyRepository.findPolicyCoverageProjectionByPolicyIdAndCustomerId(policyId, callingUserId);
        if (p == null) {
            throw new ResourceNotFoundException("Policy", String.valueOf(policyId));
        }

        return new PolicyCoverageDto(
                p.getPolicyId(),
                p.getPolicyNumber(),
                p.getStatus(),
                p.getProductType(),
                p.getCoveragePlanName(),
                p.getDeductibleCents(),
                p.getCoverageLimitCents(),
                p.getRenewalDate()
        );
    }
}
