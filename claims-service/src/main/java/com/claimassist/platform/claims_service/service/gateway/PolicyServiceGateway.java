package com.claimassist.platform.claims_service.service.gateway;

import com.claimassist.platform.claims_service.client.PolicyServiceClient;
import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class PolicyServiceGateway implements PolicyCoverageGateway {

    private static final String INSTANCE = "policyService";

    private final PolicyServiceClient policyServiceClient;
    private final EventLogger eventLogger;

    @CircuitBreaker(name = INSTANCE, fallbackMethod = "policyFallback")
    @Retry(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    @RateLimiter(name = INSTANCE)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PolicyCoverageDto getPolicyCoverage(Long policyId, Long targetUserId) {
        return policyServiceClient.getPolicyCoverage(policyId, targetUserId);
    }

    @SuppressWarnings("unused")
    private PolicyCoverageDto policyFallback(Long policyId, Long targetUserId, Throwable t) {
        log.error("policy-service unavailable while verifying policy {}: {}", policyId, t.getMessage());
        try {
            eventLogger.logBusinessEvent(null, null, java.util.Map.of(
                    "event", "policy.service.unavailable",
                    "policyId", policyId,
                    "error", t.getMessage()
            ));
        } catch (Exception ignored) {}
        throw new ServiceUnavailableException("Unable to verify your policy right now. Please try again shortly.");
    }
}
