package com.claimassist.platform.agent_service.service.gateway;

import com.claimassist.platform.agent_service.client.ClaimsClient;
import com.claimassist.platform.common_lib.dto.ClaimDocumentSummaryDto;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.enums.ClaimPermission;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Every outbound call from agent-service to claims-service goes through here,
 * not through ClaimsClient directly - required for Resilience4j's proxy-based
 * annotations to apply (no self-invocation), and to keep the
 * "what happens when claims-service is unreachable" decision in one place.
 * <p>
 * Different strategy per call, deliberately: authorization (checkPermission)
 * fails CLOSED - never let the agent act on stale/guessed authorization.
 * Read-only context (status, documents) degrades GRACEFULLY - a transient
 * claims-service hiccup shouldn't abort an entire in-flight agent response;
 * better to answer "I can't check that right now" than crash the chat.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClaimsServiceGateway {

    private static final String INSTANCE = "claimsService";

    private final ClaimsClient claimsClient;

    @CircuitBreaker(name = INSTANCE, fallbackMethod = "statusFallback")
    @Retry(name = INSTANCE)
    @TimeLimiter(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    @RateLimiter(name = INSTANCE)
    public ClaimStatusDto getClaimStatus(Long claimId) {
        return claimsClient.getClaimStatus(claimId);
    }

    @CircuitBreaker(name = INSTANCE, fallbackMethod = "documentsFallback")
    @Retry(name = INSTANCE)
    @TimeLimiter(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    @RateLimiter(name = INSTANCE)
    public List<ClaimDocumentSummaryDto> getClaimDocuments(Long claimId) {
        return claimsClient.getClaimDocuments(claimId);
    }

    @CircuitBreaker(name = INSTANCE, fallbackMethod = "permissionFallback")
    @Retry(name = INSTANCE)
    @TimeLimiter(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    @RateLimiter(name = INSTANCE)
    public boolean checkPermission(Long claimId, ClaimPermission permission) {
        return claimsClient.checkPermission(claimId, permission);
    }

    @SuppressWarnings("unused")
    private ClaimStatusDto statusFallback(Long claimId, Throwable t) {
        log.warn("claims-service unavailable fetching status for claim {}: {}", claimId, t.getMessage());
        return new ClaimStatusDto(claimId, null, "UNKNOWN", "UNAVAILABLE", "UNKNOWN", null, null, List.of());
    }

    @SuppressWarnings("unused")
    private List<ClaimDocumentSummaryDto> documentsFallback(Long claimId, Throwable t) {
        log.warn("claims-service unavailable fetching documents for claim {}: {}", claimId, t.getMessage());
        return List.of();
    }

    @SuppressWarnings("unused")
    private boolean permissionFallback(Long claimId, ClaimPermission permission, Throwable t) {
        if (t instanceof FeignException.Unauthorized || t.getCause() instanceof FeignException.Unauthorized) {
            throw new CredentialsExpiredException("JWT token is expired or invalid");
        }
        log.error("claims-service failed during permission check for claim {}: {}", claimId, t.getMessage());
        return false; // fail closed
    }
}
