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

    // sync = true: this is the platform's hottest read path - it backs the
    // claim-submission ACTIVE validation in claims-service, the agent
    // get_policy_coverage tool call, and the customer's own coverage view.
    // With a 10-minute TTL the load (policy lookup + nested coverage-plan
    // snapshot) is recomputed on expiry; single-flight collapses concurrent
    // misses on the same (policyId, caller) key to one DB load. JVM-local,
    // per-key (not a global lock), and identical to the sync=true already
    // approved for claimStatus/claimPermissionLookup in Phase 10 Task 10.1.
    @Cacheable(cacheNames = RedisCacheConfig.POLICY_COVERAGE_CACHE, key = "#policyId + '-' + #callingUserId", sync = true)
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
     * Called from wherever a Policy's status/plan is mutated. Eviction IS
     * wired today: PolicyServiceImpl.updatePolicy and deletePolicy both call
     * this (alongside evictMyPolicies) after the DB write, so a cancelled /
     * renewed policy stops answering from a stale cache immediately for the
     * owning customer - the cache key here (policyId-customerId) matches the
     * read key (policyId-callingUserId) because the caller is the owner on both
     * paths. Residual staleness is the standard cache-aside evict-vs-read race,
     * bounded by POLICY_COVERAGE_CACHE's 10-minute TTL, plus the fact that the
     * eviction fires before the surrounding transaction commits. That 10-minute
     * bound is acceptable for chat/customer answers but is why the claims-side
     * ACTIVE validation must keep failing closed (CustomerServiceGateway throws
     * on any non-ACTIVE / unavailable response) rather than trusting a cached
     * status for correctness-critical authorization.
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
