package com.claimassist.platform.claims_service.client;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "policy-service", url = "${POLICY_SERVICE_URI:http://localhost:8084}")
public interface PolicyServiceClient {

    @GetMapping("/internal/v1/policies/{policyId}/coverage")
    PolicyCoverageDto getPolicyCoverage(@PathVariable Long policyId,
                                       @RequestHeader("X-User-Id") Long userId);
}
