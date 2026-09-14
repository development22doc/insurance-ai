package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.policy_service.config.RedisCacheConfig;
import com.claimassist.platform.policy_service.dto.CustomerPolicyDetailDto;
import com.claimassist.platform.policy_service.dto.CustomerPolicySummaryDto;
import com.claimassist.platform.policy_service.entity.CustomerPolicy;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.repository.CustomerPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CustomerPolicyReadService {

    private final CustomerPolicyRepository customerPolicyRepository;
    private final CurrentUserProvider currentUserProvider;

    public List<CustomerPolicySummaryDto> getMyPolicies() {
        Long customerId = currentUserProvider.getCurrentUserId();
        return getPoliciesForCustomer(customerId);
    }

    public CustomerPolicyDetailDto getMyPolicy(Long policyId) {
        Long customerId = currentUserProvider.getCurrentUserId();
        return getPolicyForCustomer(customerId, policyId);
    }

    @Cacheable(value = RedisCacheConfig.CUSTOMER_POLICIES_CACHE, key = "T(com.claimassist.platform.policy_service.service.CustomerPolicyReadService).customerPoliciesListKey(#customerId)", sync = true)
    public List<CustomerPolicySummaryDto> getPoliciesForCustomer(Long customerId) {
        log.debug("Loading customer policy list for customerId={}", customerId);
        return customerPolicyRepository.findByCustomerIdOrderByEffectiveDateDesc(customerId)
                .stream()
                .map(this::toSummaryDto)
                .toList();
    }

    @Cacheable(value = RedisCacheConfig.CUSTOMER_POLICY_DETAILS_CACHE, key = "T(com.claimassist.platform.policy_service.service.CustomerPolicyReadService).customerPolicyDetailKey(#customerId, #policyId)", sync = true)
    public CustomerPolicyDetailDto getPolicyForCustomer(Long customerId, Long policyId) {
        log.debug("Loading customer policy detail for customerId={} policyId={}", customerId, policyId);
        CustomerPolicy customerPolicy = customerPolicyRepository.findByIdAndCustomerId(policyId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(policyId)));

        return toDetailDto(customerPolicy);
    }

    public static String customerPoliciesListKey(Long customerId) {
        return "customer:" + customerId + ":policies";
    }

    public static String customerPolicyDetailKey(Long customerId, Long policyId) {
        return "customer:" + customerId + ":policy:" + policyId;
    }

    private CustomerPolicySummaryDto toSummaryDto(CustomerPolicy customerPolicy) {
        Plan plan = customerPolicy.getPlan();
        Product product = plan.getProduct();

        return new CustomerPolicySummaryDto(
                customerPolicy.getId(),
                customerPolicy.getPolicyNumber(),
                customerPolicy.getStatus(),
                customerPolicy.getEffectiveDate(),
                customerPolicy.getRenewalDate(),
                product.getId(),
                product.getName(),
                product.getType().name(),
                plan.getId(),
                plan.getName(),
                plan.getAnnualPremiumCents(),
                plan.getDeductibleCents(),
                plan.getCoverageLimitCents(),
                plan.getCurrency()
        );
    }

    private CustomerPolicyDetailDto toDetailDto(CustomerPolicy customerPolicy) {
        Plan plan = customerPolicy.getPlan();
        Product product = plan.getProduct();

        return new CustomerPolicyDetailDto(
                customerPolicy.getId(),
                customerPolicy.getPolicyNumber(),
                customerPolicy.getStatus(),
                customerPolicy.getEffectiveDate(),
                customerPolicy.getRenewalDate(),
                customerPolicy.getActivatedAt(),
                customerPolicy.getCancelledAt(),
                product.getId(),
                product.getName(),
                product.getType().name(),
                plan.getId(),
                plan.getName(),
                plan.getAnnualPremiumCents(),
                plan.getDeductibleCents(),
                plan.getCoverageLimitCents(),
                plan.getCurrency()
        );
    }
}
