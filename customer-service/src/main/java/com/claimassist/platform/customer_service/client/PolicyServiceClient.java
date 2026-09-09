package com.claimassist.platform.customer_service.client;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.customer_service.dto.PolicySummaryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Contract for Policy Service integration retained only for legitimate service-to-service reads.
 * Customer Service no longer owns a runtime policy domain or CRUD API.
 */
@FeignClient(name = "policy-service", url = "${policy-service.uri:http://localhost:8084}")
public interface PolicyServiceClient {

    @GetMapping("/internal/v1/policies/{policyId}")
    PolicySummaryDto getPolicyById(
            @PathVariable("policyId") Long policyId,
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId);

    @GetMapping("/internal/v1/policies/customer/{customerId}")
    java.util.List<PolicySummaryDto> getPoliciesForCustomer(
            @PathVariable("customerId") Long customerId,
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId);

    @GetMapping("/internal/v1/policies/{policyId}/coverage")
    PolicyCoverageDto getPolicyCoverage(
            @PathVariable("policyId") Long policyId,
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId);
}

