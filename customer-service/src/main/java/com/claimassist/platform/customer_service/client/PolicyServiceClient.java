package com.claimassist.platform.customer_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for calling Policy Service's internal policy creation endpoint.
 * This client is prepared for migration but NOT activated in existing Customer policy flow.
 *
 * Phase 6: Integration preparation only. No cutover performed.
 */
@FeignClient(name = "policy-service", url = "${policy-service.uri:http://localhost:8084}")
public interface PolicyServiceClient {

    /**
     * Calls Policy Service's internal policy creation endpoint.
     *
     * @param request Policy creation request
     * @param xUserId X-User-Id header for acting user identity
     * @param idempotencyKey Idempotency-Key header for idempotent requests
     * @return Policy creation response
     */
    @PostMapping("/internal/v1/policies")
    PolicyCreateResponseDto createPolicy(
            @RequestBody PolicyCreateRequestDto request,
            @RequestHeader("X-User-Id") Long xUserId,
            @RequestHeader("Idempotency-Key") String idempotencyKey);

    @GetMapping("/internal/v1/policies/{policyId}")
    com.claimassist.platform.customer_service.dto.PolicySummaryDto getPolicyById(
            @PathVariable("policyId") Long policyId,
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId);

    @GetMapping("/internal/v1/policies/customer/{customerId}")
    java.util.List<com.claimassist.platform.customer_service.dto.PolicySummaryDto> getPoliciesForCustomer(
            @PathVariable("customerId") Long customerId,
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId);

    @GetMapping("/internal/v1/policies/{policyId}/coverage")
    com.claimassist.platform.common_lib.dto.PolicyCoverageDto getPolicyCoverage(
            @PathVariable("policyId") Long policyId,
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId);

    // Lifecycle delegation endpoints (call public Policy Service API and forward Authorization)
    @PostMapping("/api/v1/policies/{policyId}/cancel")
    java.util.Map<String, Object> cancelPolicy(
            @PathVariable("policyId") Long policyId,
            @RequestBody com.claimassist.platform.customer_service.dto.policy.CancelRequestDto body,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader);

    @PostMapping("/api/v1/policies/{policyId}/reinstate")
    java.util.Map<String, Object> reinstatePolicy(
            @PathVariable("policyId") Long policyId,
            @RequestBody com.claimassist.platform.customer_service.dto.policy.ReinstateRequestDto body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader);
}

