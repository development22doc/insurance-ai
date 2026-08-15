package com.claimassist.platform.claims_service.service.gateway;

import com.claimassist.platform.claims_service.client.CustomerClient;
import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every outbound call from claims-service to customer-service goes through
 * here, not through CustomerClient directly - required for Resilience4j's
 * proxy-based annotations to actually apply (see the Lovable-clone platform's
 * PRODUCTION_READINESS.md for the full self-invocation rationale) and to keep
 * "what do we do when customer-service is down" in one place.
 * <p>
 * FAILS CLOSED: verifying policy ownership/validity gates whether a claim can
 * even be opened - if we can't confirm the policy is real and ACTIVE, we
 * refuse to create the claim rather than trusting an unverified policyId.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceGateway {

    private static final String INSTANCE = "customerService";

    private final CustomerClient customerClient;
    private final EventLogger eventLogger;

    @CircuitBreaker(name = INSTANCE, fallbackMethod = "policyFallback")
    @Retry(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    @RateLimiter(name = INSTANCE)
    // NOT_SUPPORTED: this is a pure read-only remote validation call. It must NOT hold the
    // caller's DB connection (and the pool occupancy / throughput that implies) while awaiting
    // customer-service, including across resilience4j retries. Suspending the surrounding
    // transaction for the duration of this Feign call releases the connection back to the pool;
    // the surrounding transaction (claim + claimParty + claimStatusHistory + idempotency record)
    // is resumed atomically afterwards. Replay semantics are preserved because the idempotency
    // check in ClaimCommandServiceImpl.submitClaim still runs BEFORE this call is reached on the
    // fresh path, and replays return the cached response without invoking getPolicyCoverage.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PolicyCoverageDto getPolicyCoverage(Long policyId) {
        return customerClient.getPolicyCoverage(policyId);
    }

    @SuppressWarnings("unused")
    private PolicyCoverageDto policyFallback(Long policyId, Throwable t) {
        log.error("customer-service unavailable while verifying policy {}: {}", policyId, t.getMessage());
        try {
            eventLogger.logBusinessEvent(null, null, java.util.Map.of(
                    "event", "customer.service.unavailable",
                    "policyId", policyId,
                    "error", t.getMessage()
            ));
        } catch (Exception ignored) {}
        throw new ServiceUnavailableException("Unable to verify your policy right now. Please try again shortly.");
    }
}
