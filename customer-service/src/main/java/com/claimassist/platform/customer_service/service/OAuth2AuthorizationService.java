package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.config.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

/**
 * PKCE authorization request handling.
 *
 * <p>Phase 2 hardened the PKCE state store. The previous in-memory
 * {@code ConcurrentHashMap} was not production-safe: it did not survive service
 * restart, was not shared across application instances, never expired, and could
 * not be atomically consumed for replay protection. It is now backed by Redis
 * ({@link StringRedisTemplate}):
 *
 * <ul>
 *   <li><b>TTL</b> - every state is written with an expiry, so a never-completed
 *       authorize request cannot accumulate forever.</li>
 *   <li><b>One-time consumption / replay protection</b> - {@code GETDEL} is used
 *       to atomically read-and-delete the state, so the same authorization code
 *       exchange can succeed exactly once; a replayed callback finds nothing and
 *       is rejected.</li>
 *   <li><b>Multi-instance / restart safe</b> - state lives in Redis, shared by all
 *       instances, surviving restart.</li>
 *   <li><b>No secrets in logs</b> - only the (opaque) state is ever logged; the
 *       code verifier is never logged.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthorizationService {

    private static final String PKCE_STATE_KEY_PREFIX = "pkce:state:";

    private final PkceService pkceService;
    private final KeycloakProperties keycloakProperties;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${security.pkce.state-ttl-seconds:600}")
    private long stateTtlSeconds;

    public AuthorizationRequest createAuthorizationRequest() {

        log.info("Starting createAuthorizationRequest");

        if (keycloakProperties == null) {
            log.error("KeycloakProperties is null!");
            throw new IllegalStateException("KeycloakProperties is not configured");
        }

        String state = pkceService.generateState();

        String codeVerifier = pkceService.generateCodeVerifier();

        String codeChallenge =
                pkceService.generateCodeChallenge(codeVerifier);

        // Persist state -> code_verifier in Redis with a TTL. GETDEL on consume
        // guarantees one-time use; the TTL bounds the lifetime of orphaned states.
        stringRedisTemplate.opsForValue().set(
                keyFor(state),
                codeVerifier,
                Duration.ofSeconds(stateTtlSeconds));

        String authorizationUri = keycloakProperties.authorizationUri();

        if (authorizationUri == null || authorizationUri.isEmpty()) {
            log.error("Authorization URI is null or empty!");
            throw new IllegalStateException("Authorization URI could not be constructed");
        }

        String authorizationUrl =
                UriComponentsBuilder
                        .fromHttpUrl(authorizationUri)
                        .queryParam("client_id", keycloakProperties.clientId())
                        .queryParam("response_type", "code")
                        .queryParam("scope", "openid profile email")
                        .queryParam("redirect_uri", keycloakProperties.redirectUri())
                        .queryParam("code_challenge", codeChallenge)
                        .queryParam("code_challenge_method", "S256")
                        .queryParam("state", state)
                        .build()
                        .encode()
                        .toUriString();

        log.info("Authorization URL constructed successfully");

        return new AuthorizationRequest(
                authorizationUrl,
                state
        );
    }

    /**
     * Atomically consumes and removes the code verifier for the given state.
     * Because Redis {@code GETDEL} is atomic, a given state can be consumed
     * exactly once - replaying an authorization callback with an already-used
     * state returns {@code null}, which the caller rejects.
     *
     * @return the code verifier for the state, or {@code null} if the state is
     *         unknown, expired, or already consumed.
     */
    public String consumeCodeVerifier(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        return stringRedisTemplate.opsForValue().getAndDelete(keyFor(state));
    }

    private String keyFor(String state) {
        return PKCE_STATE_KEY_PREFIX + state;
    }

    public record AuthorizationRequest(
            String authorizationUrl,
            String state
    ) {
    }

}