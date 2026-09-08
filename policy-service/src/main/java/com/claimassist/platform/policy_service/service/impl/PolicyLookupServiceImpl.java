package com.claimassist.platform.policy_service.service.impl;

import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.dto.PolicySummaryDto;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.Policy;
import com.claimassist.platform.policy_service.repository.PolicyRepository;
import com.claimassist.platform.policy_service.service.PolicyLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyLookupServiceImpl implements PolicyLookupService {

    private final PolicyRepository policyRepository;

    @Override
    public PolicySummaryDto getPolicy(Long policyId, Long effectiveCustomerId) {
        Policy policy = policyRepository.findByIdAndCustomerId(policyId, effectiveCustomerId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(policyId)));
        return map(policy);
    }

    @Override
    public PolicySummaryDto getPolicyForAdmin(Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(policyId)));
        return map(policy);
    }

    @Override
    public List<PolicySummaryDto> getPoliciesForCustomer(Long customerId, Long effectiveCustomerId) {
        if (!customerId.equals(effectiveCustomerId)) {
            throw new AccessDeniedException("Not authorized to view policies for customer " + customerId);
        }
        return policyRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::map)
                .toList();
    }

    private PolicySummaryDto map(Policy policy) {
        Plan plan = policy.getCoveragePlan();
        if (plan == null || plan.getProduct() == null) {
            throw new IllegalStateException("Policy " + policy.getId() + " is missing plan/product data");
        }
        return new PolicySummaryDto(
                policy.getId(),
                policy.getPolicyNumber(),
                policy.getStatus(),
                plan.getProduct().getCode(),
                plan.getName(),
                policy.getEffectiveDate(),
                policy.getRenewalDate()
        );
    }
}
