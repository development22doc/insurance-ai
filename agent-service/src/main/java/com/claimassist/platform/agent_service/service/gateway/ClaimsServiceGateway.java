package com.claimassist.platform.agent_service.service.gateway;

import com.claimassist.platform.agent_service.cache.CacheService;
import com.claimassist.platform.agent_service.client.ClaimsClient;
import com.claimassist.platform.agent_service.config.CacheProperties;
import com.claimassist.platform.common_lib.dto.ClaimDocumentSummaryDto;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.claimassist.platform.common_lib.enums.ClaimPermission;
import com.fasterxml.jackson.core.type.TypeReference;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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
    private final CacheService cacheService;
    private final CacheProperties cacheProperties;

    @CircuitBreaker(name = INSTANCE, fallbackMethod = "statusFallback")
    @Retry(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    @RateLimiter(name = INSTANCE)
    public ClaimStatusDto getClaimStatus(Long claimId) {
        String key = cacheService.key("get_claim_status", "claim", claimId);
        return cacheService.getOrLoad(
                key, new TypeReference<ClaimStatusDto>() {}, cacheProperties.getClaimStatusTtl(),
                () -> claimsClient.getClaimStatus(claimId),
                this::isCacheableStatus);
    }

    @CircuitBreaker(name = INSTANCE, fallbackMethod = "documentsFallback")
    @Retry(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    @RateLimiter(name = INSTANCE)
    public List<ClaimDocumentSummaryDto> getClaimDocuments(Long claimId) {
        String key = cacheService.key("get_claim_documents", "claim", claimId);
        return cacheService.getOrLoad(
                key, new TypeReference<List<ClaimDocumentSummaryDto>>() {},
                cacheProperties.getClaimDocumentsTtl(),
                () -> claimsClient.getClaimDocuments(claimId),
                this::isCacheableDocuments);
    }

    @CircuitBreaker(name = INSTANCE, fallbackMethod = "permissionFallback")
    @Retry(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    @RateLimiter(name = INSTANCE)
    public boolean checkPermission(Long claimId, ClaimPermission permission) {
        return claimsClient.checkPermission(claimId, permission);
    }

    /**
     * Invalidate the read caches for a claim after a successful write (e.g. a
     * proposed claim update) so the next read returns fresh data. Called AFTER
     * the business operation is accepted, never before.
     */
    public void evictClaimStatus(Long claimId) {
        cacheService.evict(cacheService.key("get_claim_status", "claim", claimId));
        cacheService.evict(cacheService.key("get_claim_documents", "claim", claimId));
    }

    /** Never cache placeholders - only real, current statuses. */
    private boolean isCacheableStatus(ClaimStatusDto s) {
        return s != null && !"NOT_FOUND".equals(s.status()) && !"UNAVAILABLE".equals(s.status());
    }

    /** Never cache the empty list produced by the unavailable-fallback. */
    private boolean isCacheableDocuments(List<ClaimDocumentSummaryDto> docs) {
        return docs != null && !docs.isEmpty();
    }

    @SuppressWarnings("unused")
    private ClaimStatusDto statusFallback(Long claimId, Throwable t) {
        if (isNotFound(t)) {
            log.warn("claims-service returned NOT FOUND for claim {}: {}", claimId, t.getMessage());
            return new ClaimStatusDto(claimId, null, null, "NOT_FOUND", "UNKNOWN", null, null, List.of());
        }
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

    private static boolean isNotFound(Throwable t) {
        Throwable probe = t;
        while (probe != null) {
            if (probe instanceof FeignException fe && fe.status() == 404) {
                return true;
            }
            probe = probe.getCause();
        }
        return false;
    }
}
