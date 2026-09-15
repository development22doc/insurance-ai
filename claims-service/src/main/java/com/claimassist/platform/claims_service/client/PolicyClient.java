package com.claimassist.platform.claims_service.client;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;

/**
 * Feign client for Policy Service internal coverage API.
 * Replaces Customer Service as the authoritative source for policy coverage validation.
 */
@FeignClient(name = "policy-service", url = "${POLICY_SERVICE_URI:http://localhost:8084}")
public interface PolicyClient {

    /**
     * Get policy coverage for a specific customer at a point in time.
     * Used by Claims Service to validate policy coverage before accepting a claim.
     *
     * @param policyId The policy contract ID (PolicyContract.id)
     * @param asOf The incident/loss time for coverage validation (defaults to now if null)
     * @param xUserIdHeader The target customer ID (required for service-to-service calls)
     * @return Policy coverage information if policy is active at the given time
     */
    @GetMapping("/internal/v1/policies/{policyId}/coverage")
    PolicyCoverageDto getPolicyCoverage(
            @PathVariable Long policyId,
            @RequestParam(value = "asOf", required = false) Instant asOf,
            @RequestHeader("X-User-Id") Long xUserIdHeader);

}
