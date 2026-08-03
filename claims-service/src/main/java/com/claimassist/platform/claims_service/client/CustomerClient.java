package com.claimassist.platform.claims_service.client;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "customer-service", url = "${CUSTOMER_SERVICE_URI:}")
public interface CustomerClient {

    /**
     * Used when a claim is submitted, to confirm the policy exists, belongs to
     * the caller, and is ACTIVE before a claim can be opened against it -
     * claims-service never trusts a client-supplied policyId at face value.
     */
    @GetMapping("/internal/v1/policies/{policyId}/coverage")
    PolicyCoverageDto getPolicyCoverage(@PathVariable Long policyId);
}
