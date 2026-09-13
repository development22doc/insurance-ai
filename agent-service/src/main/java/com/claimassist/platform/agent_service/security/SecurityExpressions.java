package com.claimassist.platform.agent_service.security;

import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGatewayApi;
import com.claimassist.platform.common_lib.enums.ClaimPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * agent-service holds no claim-party data of its own - "can this user talk to
 * the agent about this claim" is delegated entirely to claims-service (the
 * single source of truth for claim RBAC), via the resilient
 * ClaimsServiceGateway (fails closed on any error - see its Javadoc).
 */
public class SecurityExpressions {

    private final ClaimsServiceGatewayApi claimsServiceGateway;
    private final org.springframework.web.reactive.function.client.WebClient claimsServiceWebClient;

    // Primary constructor used by Spring DI
    public SecurityExpressions(ClaimsServiceGatewayApi claimsServiceGateway,
                               org.springframework.web.reactive.function.client.WebClient claimsServiceWebClient) {
        this.claimsServiceGateway = claimsServiceGateway;
        this.claimsServiceWebClient = claimsServiceWebClient;
    }

    // Backwards-compatible constructor for tests and legacy callers
    public SecurityExpressions(ClaimsServiceGatewayApi claimsServiceGateway) {
        this.claimsServiceGateway = claimsServiceGateway;
        this.claimsServiceWebClient = org.springframework.web.reactive.function.client.WebClient.create();
    }

    /**
     * Reactive permission check delegated to claims-service. Returns a Mono<Boolean>
     * which Spring Security's reactive method security will evaluate.
     */
    public reactor.core.publisher.Mono<Boolean> canAccessClaim(Long claimId) {
        return org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
                .flatMap(securityContext -> {
                    org.springframework.security.core.Authentication auth = securityContext.getAuthentication();
                    String token = extractBearerToken(auth);
                    return callClaimsPermissionCheck(claimId, token);
                })
                .switchIfEmpty(callClaimsPermissionCheck(claimId, null))
                .defaultIfEmpty(false);
    }

    private reactor.core.publisher.Mono<Boolean> callClaimsPermissionCheck(Long claimId, String bearerToken) {
        return claimsServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/v1/claims/{claimId}/permissions/check")
                        .queryParam("permission", ClaimPermission.VIEW)
                        .build(claimId))
                .headers(h -> {
                    if (bearerToken != null && !bearerToken.isBlank()) {
                        h.setBearerAuth(bearerToken);
                    }
                })
                .retrieve()
                .bodyToMono(Boolean.class)
                .onErrorResume(e -> {
                    org.slf4j.LoggerFactory.getLogger(SecurityExpressions.class)
                            .warn("claims-service permission check failed for claim {}: {}", claimId, e.toString());
                    return reactor.core.publisher.Mono.just(false);
                });
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
