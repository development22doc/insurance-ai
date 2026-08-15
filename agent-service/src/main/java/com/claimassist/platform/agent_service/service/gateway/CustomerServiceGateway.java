package com.claimassist.platform.agent_service.service.gateway;

import com.claimassist.platform.agent_service.client.CustomerClient;
import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Coverage lookups degrade gracefully (a placeholder DTO, not a thrown
 * exception) - same reasoning as ClaimsServiceGateway's read-only calls: a
 * transient customer-service hiccup shouldn't abort the whole agent turn.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceGateway {

    private static final String INSTANCE = "customerService";

    private final CustomerClient customerClient;

    @CircuitBreaker(name = INSTANCE, fallbackMethod = "policyFallback")
    @Retry(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    @RateLimiter(name = INSTANCE)
    public PolicyCoverageDto getPolicyCoverage(Long policyId) {
        return customerClient.getPolicyCoverage(policyId);
    }

    @SuppressWarnings("unused")
    private PolicyCoverageDto policyFallback(Long policyId, Throwable t) {
        log.warn("customer-service unavailable fetching coverage for policy {}: {}", policyId, t.getMessage());
        return new PolicyCoverageDto(policyId, "UNKNOWN", "UNAVAILABLE", "UNKNOWN", "UNKNOWN", null, null, null);
    }
}
