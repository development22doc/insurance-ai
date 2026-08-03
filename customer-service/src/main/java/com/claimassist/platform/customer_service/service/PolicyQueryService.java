package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.config.RedisCacheConfig;
import com.claimassist.platform.customer_service.dto.policy.PolicyResponse;
import com.claimassist.platform.customer_service.entity.CoveragePlan;
import com.claimassist.platform.customer_service.entity.Policy;
import com.claimassist.platform.customer_service.mapper.PolicyMapper;
import com.claimassist.platform.customer_service.repository.CoveragePlanRepository;
import com.claimassist.platform.customer_service.repository.PolicyRepository;
import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Backs InternalCustomerController's hottest read path. Cached per
 * (policyId, callingUserId) pair - NOT per policyId alone - because the
 * method's whole job is "does this policy belong to this caller", so the
 * cache key must include the caller or a cache hit could leak policy A's
 * data to a request that only proved ownership of policy B.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyQueryService {

    private final PolicyRepository policyRepository;
    private final CoveragePlanRepository coveragePlanRepository;
    private final PolicyMapper policyMapper;

    @Cacheable(cacheNames = RedisCacheConfig.MY_POLICIES_CACHE, key = "#customerId")
    public List<PolicyResponse> getMyPolicies(Long customerId) {
        return policyRepository.findByCustomerId(customerId).stream()
                .map(policyMapper::toPolicyResponse)
                .toList();
    }

    @Cacheable(cacheNames = RedisCacheConfig.POLICY_COVERAGE_CACHE, key = "#policyId + '-' + #callingUserId")
    public PolicyCoverageDto getPolicyCoverage(Long policyId, Long callingUserId) {
        Policy policy = policyRepository.findByIdAndCustomerId(policyId, callingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy", policyId.toString()));

        CoveragePlanSnapshot plan = getCoveragePlanSnapshot(policy.getCoveragePlan().getId());

        return new PolicyCoverageDto(
                policy.getId(),
                policy.getPolicyNumber(),
                policy.getStatus(),
                plan.productType(),
                plan.name(),
                plan.deductibleCents(),
                plan.coverageLimitCents(),
                policy.getRenewalDate() != null ? policy.getRenewalDate().toString() : null
        );
    }

    @Cacheable(cacheNames = RedisCacheConfig.REFERENCE_DATA_CACHE, key = "'coveragePlan-' + #coveragePlanId")
    public CoveragePlanSnapshot getCoveragePlanSnapshot(Long coveragePlanId) {
        CoveragePlan plan = coveragePlanRepository.findById(coveragePlanId)
                .orElseThrow(() -> new ResourceNotFoundException("CoveragePlan", coveragePlanId.toString()));
        return new CoveragePlanSnapshot(
                plan.getName(),
                plan.getProductType(),
                plan.getDeductibleCents(),
                plan.getCoverageLimitCents());
    }

    /**
     * Call this from wherever a Policy's status/plan is mutated (renewal,
     * cancellation, plan change) - none of those flows are implemented in
     * this pass (see INSURANCE_AI_PLATFORM.md "documented simplifications"),
     * but whoever adds them MUST call this, or a cached ACTIVE policy could
     * keep answering "ACTIVE" for up to POLICY_COVERAGE_CACHE's 5-minute TTL
     * after a cancellation - acceptable staleness for a chat answer, NOT
     * acceptable for the claim-submission ACTIVE check in claims-service, so
     * that check should eventually call a non-cached path or a short-TTL one.
     */
    @CacheEvict(cacheNames = RedisCacheConfig.POLICY_COVERAGE_CACHE, key = "#policyId + '-' + #customerId")
    public void evictPolicyCoverage(Long policyId, Long customerId) {
        // no-op body - the annotation does the work
    }

    @CacheEvict(cacheNames = RedisCacheConfig.MY_POLICIES_CACHE, key = "#customerId")
    public void evictMyPolicies(Long customerId) {
        // no-op body - the annotation does the work
    }

    @CacheEvict(cacheNames = RedisCacheConfig.REFERENCE_DATA_CACHE, key = "'coveragePlan-' + #coveragePlanId")
    public void evictCoveragePlanSnapshot(Long coveragePlanId) {
        // no-op body - the annotation does the work
    }

    public record CoveragePlanSnapshot(
            String name,
            String productType,
            Long deductibleCents,
            Long coverageLimitCents) {
    }
}
