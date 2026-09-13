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
import org.springframework.security.core.Authentication;

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
@Slf4j
public class ClaimsServiceGateway implements ClaimsServiceGatewayApi {

    private static final String INSTANCE = "claimsService";

    private final ClaimsClient claimsClient;
    private final CacheService cacheService;
    private final CacheProperties cacheProperties;
    private final org.springframework.web.reactive.function.client.WebClient claimsServiceWebClient;

    public ClaimsServiceGateway(ClaimsClient claimsClient,
                               CacheService cacheService,
                               CacheProperties cacheProperties,
                               org.springframework.web.reactive.function.client.WebClient claimsServiceWebClient) {
        this.claimsClient = claimsClient;
        this.cacheService = cacheService;
        this.cacheProperties = cacheProperties;
        this.claimsServiceWebClient = claimsServiceWebClient;
    }

    // Backwards-compatible constructor for tests and legacy callers
    public ClaimsServiceGateway(ClaimsClient claimsClient, CacheService cacheService, CacheProperties cacheProperties) {
        this(claimsClient, cacheService, cacheProperties,
                org.springframework.web.reactive.function.client.WebClient.create());
    }

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

    /**
     * Reactive, non-blocking variant for use in WebFlux request paths. Returns
     * an empty Mono when the remote service indicates NOT_FOUND or on 404.
     */
    public reactor.core.publisher.Mono<ClaimStatusDto> getClaimStatusReactive(Long claimId) {
        String path = "/internal/v1/claims/" + claimId + "/status";

        return org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
                .flatMap(securityContext -> {
                    org.springframework.security.core.Authentication auth = securityContext.getAuthentication();
                    String token = extractBearerToken(auth);

                    return claimsServiceWebClient.get()
                            .uri(path)
                            .headers(h -> {
                                if (token != null && !token.isBlank()) {
                                    h.setBearerAuth(token);
                                }
                            })
                            .retrieve()
                            .onStatus(status -> status.value() == 404, resp -> reactor.core.publisher.Mono.empty())
                            .bodyToMono(ClaimStatusDto.class);
                })
                .switchIfEmpty(reactor.core.publisher.Mono.empty())
                .onErrorResume(ex -> reactor.core.publisher.Mono.empty());
    }

    /**
     * Reactive claim status fetch that accepts an explicit JWT token instead of relying on
     * ReactiveSecurityContextHolder. Use this from AI tool execution which runs
     * on a non-reactive executor where the SecurityContext may not be available.
     */
    public reactor.core.publisher.Mono<ClaimStatusDto> getClaimStatusReactive(Long claimId, String jwtToken) {
        String path = "/internal/v1/claims/" + claimId + "/status";

        return claimsServiceWebClient.get()
                .uri(path)
                .headers(h -> {
                    if (jwtToken != null && !jwtToken.isBlank()) {
                        h.setBearerAuth(jwtToken);
                    }
                })
                .retrieve()
                .onStatus(status -> status.value() == 404, resp -> reactor.core.publisher.Mono.empty())
                .bodyToMono(ClaimStatusDto.class)
                .onErrorResume(ex -> reactor.core.publisher.Mono.empty());
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
     * Reactive permission check that propagates the JWT via WebClient.
     * Use this from AI tool execution which runs on a non-reactive executor
     * where Feign's interceptor cannot access the reactive SecurityContext.
     */
    public reactor.core.publisher.Mono<Boolean> checkPermissionReactive(Long claimId, ClaimPermission permission) {
        String path = "/internal/v1/claims/" + claimId + "/permissions/check?permission=" + permission;

        return org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
                .flatMap(securityContext -> {
                    org.springframework.security.core.Authentication auth = securityContext.getAuthentication();
                    String token = extractBearerToken(auth);

                    return claimsServiceWebClient.get()
                            .uri(path)
                            .headers(h -> {
                                if (token != null && !token.isBlank()) {
                                    h.setBearerAuth(token);
                                }
                            })
                            .retrieve()
                            .bodyToMono(Boolean.class)
                            .onErrorResume(ex -> reactor.core.publisher.Mono.just(false)); // fail closed
                })
                .switchIfEmpty(reactor.core.publisher.Mono.just(false)); // fail closed when no security context
    }

    /**
     * Permission check that accepts an explicit JWT token instead of relying on
     * ReactiveSecurityContextHolder. Use this from AI tool execution which runs
     * on a non-reactive executor where the SecurityContext may not be available.
     */
    public reactor.core.publisher.Mono<Boolean> checkPermissionWithToken(Long claimId, ClaimPermission permission, String jwtToken) {
        String path = "/internal/v1/claims/" + claimId + "/permissions/check?permission=" + permission;

        return claimsServiceWebClient.get()
                .uri(path)
                .headers(h -> {
                    if (jwtToken != null && !jwtToken.isBlank()) {
                        h.setBearerAuth(jwtToken);
                    }
                })
                .retrieve()
                .bodyToMono(Boolean.class)
                .onErrorResume(ex -> reactor.core.publisher.Mono.just(false)); // fail closed
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

    private String extractBearerToken(org.springframework.security.core.Authentication auth) {
        if (auth == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            return jwt.getTokenValue();
        }
        if (auth.getCredentials() instanceof CharSequence cs) {
            return cs.toString();
        }
        if (auth.getCredentials() instanceof org.springframework.security.oauth2.jwt.Jwt credJwt) {
            return credJwt.getTokenValue();
        }
        return null;
    }
}
