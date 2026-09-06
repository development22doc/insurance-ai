package com.claimassist.platform.customer_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
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
}
