package com.claimassist.platform.agent_service.client;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "customer-service", url = "${CUSTOMER_SERVICE_URI:http://localhost:8081}")
public interface CustomerClient {

    @GetMapping("/internal/v1/policies/{policyId}/coverage")
    PolicyCoverageDto getPolicyCoverage(@PathVariable Long policyId,
                                        @RequestHeader("X-User-Id") Long userId);
}
