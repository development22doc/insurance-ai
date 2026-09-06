package com.claimassist.platform.customer_service.client;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.claimassist.platform.customer_service.dto.policy.PolicyCreateRequest;
import com.claimassist.platform.customer_service.dto.policy.PolicyResponse;
import com.claimassist.platform.customer_service.entity.CoveragePlan;
import com.claimassist.platform.customer_service.repository.CoveragePlanRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;

/**
 * Adapter for Policy Service integration.
 * Handles request/response mapping, error translation, and service-to-service authentication.
 *
 * Phase 6: Integration preparation only. NOT activated in existing Customer policy flow.
 * This adapter exists as migration preparation only.
 *
 * CUSTOMER LEGACY IMPLEMENTATION MAPPING:
 * ========================================
 * The following Customer Service components will eventually be replaced by Policy Service:
 *
 * Customer Policy entity
 *     → Policy Service Policy entity
 *
 * Customer CoveragePlan entity
 *     → Policy Service Product/Plan/Coverage hierarchical structure
 *
 * Customer PolicyRepository
 *     → Policy Service PolicyRepository (in claimassist_policy DB)
 *
 * Customer CoveragePlanRepository
 *     → Policy Service ProductRepository/PlanRepository/CoverageRepository
 *
 * Customer PolicyServiceImpl.createPolicy()
 *     → Policy Service PolicyCreationService
 *
 * Customer PolicyServiceImpl.getPolicyById()
 *     → Policy Service API lookup
 *
 * Customer PolicyServiceImpl.updatePolicy()
 *     → Policy Service lifecycle management
 *
 * Customer PolicyServiceImpl.deletePolicy()
 *     → Policy Service lifecycle management
 *
 * Customer PolicyMapper
 *     → Policy Service DTOs and mappers
 *
 * Customer policy database tables (policies, coverage_plans)
 *     → Policy Service database tables (policies, policy_versions, products, plans, coverages)
 *
 * DATA MAPPING STRATEGY:
 * =====================
 * CoveragePlan.productType → Product.code
 * CoveragePlan.name → Plan.code
 * CoveragePlan-specific attributes → Coverage.code
 *
 * This mapping requires either:
 * 1. A configuration table mapping CoveragePlan.id to (productCode, planCode, coverageCode)
 * 2. Business logic to derive codes from CoveragePlan attributes
 * 3. Cross-database query (NOT recommended - violates database ownership boundaries)
 *
 * DATABASE OWNERSHIP:
 * ===================
 * Customer Service MUST NOT directly access claimassist_policy database.
 * Policy Service is the ONLY service that should access claimassist_policy.
 * All communication must go through Policy Service APIs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyServiceAdapter {

    private final PolicyServiceClient policyServiceClient;
    private final CoveragePlanRepository coveragePlanRepository;
    private final com.claimassist.platform.customer_service.config.PolicyServiceProperties policyServiceProperties;

    /**
     * Creates a policy via Policy Service.
     *
     * IMPORTANT: This method is NOT called from existing Customer policy flow.
     * It exists only for migration preparation.
     *
     * IDEMPOTENCY PROPAGATION:
     * Policy Service requires Idempotency-Key header, but Customer's current API contract
     * does not expose/receive this header. This is a contract gap that must be addressed
     * in the cutover phase by adding Idempotency-Key to Customer's external API contract.
     *
     * CRITICAL: This method does NOT generate fallback UUIDs. If idempotencyKey is null/blank,
     * the method will fail with an appropriate error. This ensures idempotency identity is
     * never silently invented or changed during propagation.
     *
     * @param customerRequest Customer's policy creation request
     * @param customerId Customer ID
     * @param idempotencyKey Idempotency key from original request (must be supplied)
     * @return Policy response
     * @throws BadRequestException if idempotencyKey is null or blank
     */
    public PolicyResponse createPolicyViaPolicyService(
            PolicyCreateRequest customerRequest,
            Long customerId,
            String idempotencyKey) {

        // Idempotency gap: Customer's current API does not expose Idempotency-Key
        // This would need to be addressed in the cutover phase
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException(
                    "Idempotency-Key is required but was not provided. " +
                    "Customer's external API contract must be updated to expose Idempotency-Key " +
                    "before Policy Service integration can be activated.");
        }

        // Fetch CoveragePlan to map to Policy Service structure
        CoveragePlan coveragePlan = coveragePlanRepository.findById(customerRequest.coveragePlanId())
                .orElseThrow(() -> new ResourceNotFoundException("CoveragePlan", String.valueOf(customerRequest.coveragePlanId())));

        // Map Customer request to Policy Service request
        PolicyCreateRequestDto policyServiceRequest;

        // First, consult configuration-based mapping for the CoveragePlan id.
        var mappingOpt = policyServiceProperties.getMappingForCoveragePlan(coveragePlan.getId());
        if (mappingOpt.isPresent()) {
            var m = mappingOpt.get();
            policyServiceRequest = new PolicyCreateRequestDto();
            policyServiceRequest.setCustomerId(customerId);
            policyServiceRequest.setProductCode(m.getProductCode());
            policyServiceRequest.setPlanCode(m.getPlanCode());
            policyServiceRequest.setCoverageCode(m.getCoverageCode());
            policyServiceRequest.setEffectiveDate(customerRequest.effectiveDate());
            policyServiceRequest.setRenewalDate(customerRequest.renewalDate());
            policyServiceRequest.setSuccessUrl(null);
            policyServiceRequest.setCancelUrl(null);
        } else {
            // No configuration mapping found - fall back to existing mapper which intentionally
            // throws when a safe mapping cannot be determined.
            try {
                policyServiceRequest = PolicyServiceMapper.toPolicyServiceRequest(
                        coveragePlan,
                        customerId,
                        customerRequest.effectiveDate(),
                        customerRequest.renewalDate(),
                        null, // successUrl - not in Customer contract
                        null  // cancelUrl - not in Customer contract
                );
            } catch (UnsupportedOperationException e) {
                // CoveragePlan → Product/Plan/Coverage mapping cannot be safely established
                // This is a deliberate block to prevent incorrect policy creation
                throw new BadRequestException(
                        "Cannot create policy via Policy Service: " + e.getMessage() +
                                " The adapter is explicitly unavailable until the mapping is resolved.");
            }
        }

        try {
            // Call Policy Service
            PolicyCreateResponseDto response = policyServiceClient.createPolicy(
                    policyServiceRequest,
                    customerId,
                    idempotencyKey
            );

            // Map response back to Customer format
            return PolicyServiceMapper.toCustomerResponse(
                    response,
                    coveragePlan.getName(),
                    coveragePlan.getProductType(),
                    customerRequest.effectiveDate(),
                    customerRequest.renewalDate()
            );

        } catch (FeignException e) {
            log.error("Policy Service call failed: status={}, message={}", e.status(), e.getMessage());
            throw mapFeignException(e);
        }
    }

    /**
     * Maps Feign exceptions to appropriate project exceptions.
     * Follows existing project error handling patterns.
     */
    private RuntimeException mapFeignException(FeignException e) {
        switch (e.status()) {
            case 400:
                return new BadRequestException("Policy Service validation failed: " + e.getMessage());
            case 403:
                return new AccessDeniedException("Policy Service access denied: " + e.getMessage());
            case 404:
                return new ResourceNotFoundException("Policy Service resource", "unknown");
            case 409:
                return new BadRequestException("Policy Service idempotency conflict: " + e.getMessage());
            case 503:
                return new ServiceUnavailableException("Policy Service unavailable: " + e.getMessage());
            default:
                return new ServiceUnavailableException("Policy Service error: " + e.getMessage());
        }
    }
}
