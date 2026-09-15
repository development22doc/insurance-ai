package com.claimassist.platform.agent_service.client;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;

@FeignClient(name = "policy-service", url = "${POLICY_SERVICE_URI:http://localhost:8084}")
public interface PolicyClient {

    @GetMapping("/internal/v1/policies/{policyId}/coverage")
    PolicyCoverageDto getPolicyCoverage(@PathVariable Long policyId,
                                        @RequestParam(value = "asOf", required = false) Instant asOf,
                                        @RequestHeader("X-User-Id") Long userId);
}

