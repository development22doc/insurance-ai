package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.policy_service.dto.PolicySummaryDto;
import com.claimassist.platform.policy_service.dto.PolicyVersionDto;
import com.claimassist.platform.policy_service.dto.ProductDto;
import com.claimassist.platform.policy_service.dto.PlanDto;

import java.util.List;

public interface PublicPolicyQueryService {

    /**
     * Get all policies with pagination (admin/operations use).
     */
    List<PolicySummaryDto> getAllPolicies(int page, int size, String sort);

    /**
     * Get current authenticated customer's policies.
     */
    List<PolicySummaryDto> getMyPolicies(Long customerId);

    /**
     * Get policy version history.
     */
    List<PolicyVersionDto> getPolicyHistory(Long policyId, Long customerId);

    /**
     * Get all products for public catalog.
     */
    List<ProductDto> getAllProducts();

    /**
     * Get a specific product by ID.
     */
    ProductDto getProduct(Long productId);

    /**
     * Get all plans for a product.
     */
    List<PlanDto> getPlansForProduct(Long productId);

    /**
     * Get a specific plan by ID.
     */
    PlanDto getPlan(Long planId);
}
