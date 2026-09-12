package com.claimassist.platform.policy_service.service.impl;

import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.config.RedisCacheConfig;
import com.claimassist.platform.policy_service.dto.PlanDto;
import com.claimassist.platform.policy_service.dto.PolicySummaryDto;
import com.claimassist.platform.policy_service.dto.PolicyVersionDto;
import com.claimassist.platform.policy_service.dto.ProductDto;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.Policy;
import com.claimassist.platform.policy_service.entity.PolicyVersion;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.PolicyRepository;
import com.claimassist.platform.policy_service.repository.PolicyVersionRepository;
import com.claimassist.platform.policy_service.repository.ProductRepository;
import com.claimassist.platform.policy_service.service.PublicPolicyQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicPolicyQueryServiceImpl implements PublicPolicyQueryService {

    private final PolicyRepository policyRepository;
    private final PolicyVersionRepository policyVersionRepository;
    private final ProductRepository productRepository;
    private final PlanRepository planRepository;

    @Override
    public List<PolicySummaryDto> getAllPolicies(int page, int size, String sort) {
        Sort.Direction direction = sort != null && sort.startsWith("-") ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortField = sort != null ? sort.replaceFirst("^-", "") : "createdAt";
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));
        Page<Policy> policies = policyRepository.findAll(pageable);
        return policies.stream().map(this::mapToSummary).toList();
    }

    @Override
    public List<PolicySummaryDto> getMyPolicies(Long customerId) {
        return policyRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::mapToSummary)
                .toList();
    }

    @Override
    public List<PolicyVersionDto> getPolicyHistory(Long policyId, Long customerId) {
        // Verify the customer has access to this policy
        Policy policy = policyRepository.findByIdAndCustomerId(policyId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(policyId)));

        List<PolicyVersion> versions = policyVersionRepository.findByPolicyIdOrderByVersionNumber(policyId);
        return versions.stream().map(this::mapToVersionDto).toList();
    }

    @Override
    @Cacheable(value = RedisCacheConfig.PRODUCT_CACHE, key = "'all'")
    public List<ProductDto> getAllProducts() {
        return productRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToProductDto)
                .toList();
    }

    @Override
    @Cacheable(value = RedisCacheConfig.PRODUCT_CACHE, key = "#productId")
    public ProductDto getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", String.valueOf(productId)));
        return mapToProductDto(product);
    }

    @Override
    @Cacheable(value = RedisCacheConfig.PLANS_BY_PRODUCT_CACHE, key = "#productId")
    public List<PlanDto> getPlansForProduct(Long productId) {
        return planRepository.findByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(this::mapToPlanDto)
                .toList();
    }

    @Override
    @Cacheable(value = RedisCacheConfig.PLAN_CACHE, key = "#planId")
    public PlanDto getPlan(Long planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", String.valueOf(planId)));
        return mapToPlanDto(plan);
    }

    private PolicySummaryDto mapToSummary(Policy policy) {
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

    private PolicyVersionDto mapToVersionDto(PolicyVersion version) {
        Plan plan = version.getPlan();
        return new PolicyVersionDto(
                version.getId(),
                version.getVersionNumber(),
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                version.getPremiumCents(),
                version.getDeductibleCents(),
                version.getCoverageLimitCents(),
                version.getEffectiveFrom(),
                version.getEffectiveTo(),
                version.getCreatedAt()
        );
    }

    private ProductDto mapToProductDto(Product product) {
        return new ProductDto(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getActive() == null ? Boolean.TRUE : product.getActive(),
                product.getCreatedAt()
        );
    }

    private PlanDto mapToPlanDto(Plan plan) {
        return new PlanDto(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getActive(),
                plan.getProduct() != null ? plan.getProduct().getId() : null,
                plan.getProduct() != null ? plan.getProduct().getCode() : null,
                plan.getDeductibleCents(),
                plan.getCoverageLimitCents(),
                plan.getCreatedAt()
        );
    }
}
