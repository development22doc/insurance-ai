package com.claimassist.platform.agent_service.service.gateway;

import com.claimassist.platform.agent_service.cache.CacheService;
import com.claimassist.platform.agent_service.client.PolicyServiceClient;
import com.claimassist.platform.agent_service.config.CacheProperties;
import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class PolicyServiceGateway implements PolicyCoverageGateway {

    private static final String INSTANCE = "policyService";

    private final PolicyServiceClient policyServiceClient;
    private final CacheService cacheService;
    private final CacheProperties cacheProperties;

    @CircuitBreaker(name = INSTANCE, fallbackMethod = "policyFallback")
    @Retry(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    @RateLimiter(name = INSTANCE)
    public PolicyCoverageDto getPolicyCoverage(Long policyId, Long targetUserId) {
        String key = cacheService.key("get_policy_coverage", "policy", policyId);
        return cacheService.getOrLoad(
                key,
                new TypeReference<PolicyCoverageDto>() {},
                cacheProperties.getPolicyCoverageTtl(),
                () -> policyServiceClient.getPolicyCoverage(policyId, targetUserId),
                this::isCacheableCoverage);
    }

    private boolean isCacheableCoverage(PolicyCoverageDto c) {
        return c != null && !"NOT_FOUND".equals(c.status()) && !"UNAVAILABLE".equals(c.status());
    }

    @SuppressWarnings("unused")
    private PolicyCoverageDto policyFallback(Long policyId, Long targetUserId, Throwable t) {
        if (isNotFound(t)) {
            log.warn("policy-service returned NOT FOUND for policy {}: {}", policyId, t.getMessage());
            return new PolicyCoverageDto(policyId, null, "NOT_FOUND", "UNKNOWN", "UNKNOWN", null, null, null);
        }
        log.warn("policy-service unavailable fetching coverage for policy {}: {}", policyId, t.getMessage());
        throw new ServiceUnavailableException("Unable to retrieve the policy coverage right now.");
    }

    private static boolean isNotFound(Throwable t) {
        Throwable probe = t;
        while (probe != null) {
            if (probe instanceof feign.FeignException fe && fe.status() == 404) {
                return true;
            }
            probe = probe.getCause();
        }
        return false;
    }
}
