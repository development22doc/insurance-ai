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
     */
    public PolicyResponse createPolicyViaPolicyService(
            PolicyCreateRequest customerRequest,
            Long customerId,
            String idempotencyKey) {

        // Idempotency gap: Customer's current API does not expose Idempotency-Key
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
            try {
                policyServiceRequest = PolicyServiceMapper.toPolicyServiceRequest(
                        coveragePlan,
                        customerId,
                        customerRequest.effectiveDate(),
                        customerRequest.renewalDate(),
                        null,
                        null
                );
            } catch (UnsupportedOperationException e) {
                throw new BadRequestException(
                        "Cannot create policy via Policy Service: " + e.getMessage() +
                                " The adapter is explicitly unavailable until the mapping is resolved.");
            }
        }

        try {
            PolicyCreateResponseDto response = policyServiceClient.createPolicy(
                    policyServiceRequest,
                    customerId,
                    idempotencyKey
            );

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

    // New read delegation helpers -------------------------------------------------

    public PolicyResponse getPolicyById(Long policyId, Long actingUserId) {
        com.claimassist.platform.customer_service.dto.PolicySummaryDto svcDto = policyServiceClient.getPolicyById(policyId, actingUserId);
        if (svcDto == null) return null;
        return new PolicyResponse(svcDto.id(), svcDto.policyNumber(), svcDto.status(), svcDto.coveragePlanName(), svcDto.productType(), svcDto.effectiveDate(), svcDto.renewalDate());
    }

    public java.util.List<PolicyResponse> getPoliciesForCustomer(Long customerId, Long actingUserId) {
        java.util.List<com.claimassist.platform.customer_service.dto.PolicySummaryDto> list = policyServiceClient.getPoliciesForCustomer(customerId, actingUserId);
        return list.stream().map(svcDto -> new PolicyResponse(svcDto.id(), svcDto.policyNumber(), svcDto.status(), svcDto.coveragePlanName(), svcDto.productType(), svcDto.effectiveDate(), svcDto.renewalDate())).toList();
    }

    public com.claimassist.platform.common_lib.dto.PolicyCoverageDto getPolicyCoverage(Long policyId, Long actingUserId) {
        return policyServiceClient.getPolicyCoverage(policyId, actingUserId);
    }

    /**
     * Delegate cancel command to Policy Service public API. Forwards Authorization header from caller.
     */
    public java.util.Map<String, Object> cancelPolicy(Long policyId, com.claimassist.platform.customer_service.dto.policy.CancelRequestDto body, String authorizationHeader) {
        try {
            return policyServiceClient.cancelPolicy(policyId, body, authorizationHeader);
        } catch (FeignException e) {
            log.error("Policy Service cancel failed: status={}, message={}", e.status(), e.getMessage());
            throw mapFeignException(e);
        }
    }

    /**
     * Delegate reinstate command to Policy Service public API. Forwards Authorization and optional Idempotency-Key.
     */
    public java.util.Map<String, Object> reinstatePolicy(Long policyId, com.claimassist.platform.customer_service.dto.policy.ReinstateRequestDto body, String idempotencyKey, String authorizationHeader) {
        try {
            return policyServiceClient.reinstatePolicy(policyId, body, idempotencyKey, authorizationHeader);
        } catch (FeignException e) {
            log.error("Policy Service reinstate failed: status={}, message={}", e.status(), e.getMessage());
            throw mapFeignException(e);
        }
    }

    private RuntimeException mapFeignException(FeignException e) {
        switch (e.status()) {
            case 400:
                return new BadRequestException("Policy Service validation failed: " + e.getMessage());
            case 403:
                return new org.springframework.security.access.AccessDeniedException("Policy Service access denied: " + e.getMessage());
            case 404:
                return new ResourceNotFoundException("Policy Service resource", "unknown");
            case 409:
                return new BadRequestException("Policy Service idempotency conflict: " + e.getMessage());
            case 503:
                return new com.claimassist.platform.common_lib.error.ServiceUnavailableException("Policy Service unavailable: " + e.getMessage());
            default:
                return new com.claimassist.platform.common_lib.error.ServiceUnavailableException("Policy Service error: " + e.getMessage());
        }
    }
}
