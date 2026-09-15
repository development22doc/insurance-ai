package com.claimassist.platform.agent_service.service.gateway;

import com.claimassist.platform.agent_service.cache.CacheService;
import com.claimassist.platform.agent_service.client.PolicyClient;
import com.claimassist.platform.agent_service.config.CacheProperties;
import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Coverage lookups degrade gracefully (a placeholder DTO, not a thrown
 * exception) - same reasoning as ClaimsServiceGateway's read-only calls: a
 * transient policy-service hiccup shouldn't abort the whole agent turn.
 */
@Slf4j
public class PolicyServiceGateway implements PolicyServiceGatewayApi {

    private static final String INSTANCE = "policyService";

        private final PolicyClient policyClient;
    private final CacheService cacheService;
    private final CacheProperties cacheProperties;
    private final WebClient policyServiceWebClient;

    public PolicyServiceGateway(PolicyClient policyClient,
                                      CacheService cacheService,
                                      CacheProperties cacheProperties,
                                      WebClient policyServiceWebClient) {
            this.policyClient = policyClient;
        this.cacheService = cacheService;
        this.cacheProperties = cacheProperties;
        this.policyServiceWebClient = policyServiceWebClient;
    }

    // Backwards-compatible constructor for tests and legacy callers
    public PolicyServiceGateway(PolicyClient policyClient, CacheService cacheService, CacheProperties cacheProperties) {
            this(policyClient, cacheService, cacheProperties, WebClient.create());
    }

    @CircuitBreaker(name = INSTANCE, fallbackMethod = "policyFallback")
    @Retry(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    @RateLimiter(name = INSTANCE)
    public PolicyCoverageDto getPolicyCoverage(Long policyId, Long targetUserId) {
        String key = cacheService.key("get_policy_coverage", "policy", policyId);
        return cacheService.getOrLoad(
                key, new TypeReference<PolicyCoverageDto>() {}, cacheProperties.getPolicyCoverageTtl(),
                () -> policyClient.getPolicyCoverage(policyId, null, targetUserId),
                this::isCacheableCoverage);
    }

    /**
     * Reactive variant that propagates JWT via WebClient for use in AI tool execution
     * which runs on a non-reactive executor where Feign's interceptor cannot access
     * the reactive SecurityContext.
     */
    @Override
    public reactor.core.publisher.Mono<PolicyCoverageDto> getPolicyCoverageReactive(Long policyId, Long targetUserId, String jwtToken) {
        String path = "/internal/v1/policies/" + policyId + "/coverage";

        return policyServiceWebClient.get()
                .uri(path)
                .headers(h -> {
                    if (jwtToken != null && !jwtToken.isBlank()) {
                        h.setBearerAuth(jwtToken);
                    }
                    // For USER tokens, X-User-Id is ignored by InternalRequestIdentity
                    // For SERVICE tokens, X-User-Id is required
                    // We send it in both cases to support both authentication paths
                    h.set("X-User-Id", String.valueOf(targetUserId));
                })
                .retrieve()
                .onStatus(status -> status.value() == 401, resp -> reactor.core.publisher.Mono.empty())
                .onStatus(status -> status.value() == 404, resp -> reactor.core.publisher.Mono.empty())
                .bodyToMono(PolicyCoverageDto.class)
                .onErrorResume(ex -> reactor.core.publisher.Mono.empty());
    }

    /** Never cache placeholders - only real, current coverage. */
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
        return new PolicyCoverageDto(policyId, "UNKNOWN", "UNAVAILABLE", "UNKNOWN", "UNKNOWN", null, null, null);
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

