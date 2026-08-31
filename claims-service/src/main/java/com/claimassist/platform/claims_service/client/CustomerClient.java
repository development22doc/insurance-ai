package com.claimassist.platform.claims_service.client;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "customer-service", url = "${CUSTOMER_SERVICE_URI:http://localhost:8081}")
public interface CustomerClient {

    /**
     * Used when a claim is submitted, to confirm the policy exists, belongs to
     * the caller, and is ACTIVE before a claim can be opened against it -
     * claims-service never trusts a client-supplied policyId at face value.
     * <p>
     * F-2: the caller is a trusted service, so it must supply an explicit
     * X-User-Id for the target user (the policyholder) whose policy it is
     * verifying. customer-service checks the service token's identity against a
     * trusted allowlist and enforces policy ownership for that user.
     */
    @GetMapping("/internal/v1/policies/{policyId}/coverage")
    PolicyCoverageDto getPolicyCoverage(@PathVariable Long policyId,
                                        @RequestHeader("X-User-Id") Long userId);
}
