package com.claimassist.platform.claims_service.service.gateway;

import com.claimassist.platform.claims_service.client.PolicyClient;
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

import java.time.Instant;

/**
 * Gateway for Policy Service internal coverage API.
 * Policy Service is the authoritative owner of policy-domain validation.
 *
 * Claims must call the authoritative PolicyContract.id-based coverage endpoint and
 * fail closed if the policy cannot be validated at the incident date.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PolicyServiceGateway {

    private static final String INSTANCE = "policyService";

    private final PolicyClient policyClient;
    private final EventLogger eventLogger;

    @CircuitBreaker(name = INSTANCE, fallbackMethod = "policyFallback")
    @Retry(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    @RateLimiter(name = INSTANCE)
    // NOT_SUPPORTED: this is a pure read-only remote validation call. It must NOT hold the
    // caller's DB connection (and the pool occupancy / throughput that implies) while awaiting
    // policy-service, including across resilience4j retries. Suspending the surrounding
    // transaction for the duration of this Feign call releases the connection back to the pool;
    // the surrounding transaction (claim + claimParty + claimStatusHistory + idempotency record)
    // is resumed atomically afterwards. Replay semantics are preserved because the idempotency
    // check in ClaimCommandServiceImpl.submitClaim still runs BEFORE this call is reached on the
    // fresh path, and replays return the cached response without invoking getPolicyCoverage.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PolicyCoverageDto getPolicyCoverage(Long policyId, Long targetUserId, Instant incidentDate) {
        return policyClient.getPolicyCoverage(policyId, incidentDate, targetUserId);
    }

    @SuppressWarnings("unused")
    private PolicyCoverageDto policyFallback(Long policyId, Long targetUserId, Instant incidentDate, Throwable t) {
        log.error("policy-service unavailable while verifying policy {} for incident date {}: {}",
                policyId, incidentDate, t.getMessage());
        try {
            eventLogger.logBusinessEvent(null, null, java.util.Map.of(
                    "event", "policy.service.unavailable",
                    "policyId", policyId,
                    "incidentDate", incidentDate != null ? incidentDate.toString() : "now",
                    "error", t.getMessage()
            ));
        } catch (Exception ignored) {}
        throw new ServiceUnavailableException("Unable to verify your policy right now. Please try again shortly.");
    }
}
