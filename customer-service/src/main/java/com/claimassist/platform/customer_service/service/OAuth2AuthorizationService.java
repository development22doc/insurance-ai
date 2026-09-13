package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.config.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthorizationService {
    private static final String PKCE_STATE_KEY_PREFIX = "pkce:state:";

    private final KeycloakProperties keycloakProperties;
    private final PkceService pkceService;
    private final StringRedisTemplate stringRedisTemplate;


    @Value("${security.pkce.state-ttl-seconds:600}")
    private long stateTtlSeconds;
    private final ConcurrentHashMap<String, InMemoryEntry> inMemoryFallback = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "pkce-inmemory-cleaner");
        t.setDaemon(true);
        return t;
    });

    {
        cleanupScheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, InMemoryEntry> e : inMemoryFallback.entrySet()) {
                if (e.getValue().expiryMs <= now) {
                    inMemoryFallback.remove(e.getKey(), e.getValue());
                }
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    public AuthorizationRequest createAuthorizationRequest() {
        log.debug("Starting createAuthorizationRequest");

        String state = pkceService.generateState();
        String codeVerifier = pkceService.generateCodeVerifier();
        String codeChallenge = pkceService.generateCodeChallenge(codeVerifier);

        try {
            stringRedisTemplate.opsForValue().set(
                    keyFor(state),
                    codeVerifier,
                    Duration.ofSeconds(stateTtlSeconds));
        } catch (Exception e) {
            log.warn("Redis unavailable for PKCE state storage; using in-memory fallback: {}", e.toString());
            long expiryMs = System.currentTimeMillis() + (stateTtlSeconds * 1000);
            inMemoryFallback.put(keyFor(state), new InMemoryEntry(codeVerifier, expiryMs));
        }

        String authorizationUri = keycloakProperties.authorizationUri();
        if (authorizationUri == null || authorizationUri.isEmpty()) {
            log.error("Authorization URI is null or empty!");
            throw new IllegalStateException("Authorization URI could not be constructed");
        }

        String authorizationUrl = UriComponentsBuilder
                .fromHttpUrl(authorizationUri)
                .queryParam("client_id", keycloakProperties.clientId())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid profile email userId-claim")
                .queryParam("redirect_uri", keycloakProperties.redirectUri())
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();

        log.info("=== OAUTH AUTHORIZATION REQUEST === client_id={} response_type=code scope={} redirect_uri={} code_challenge_present=true code_challenge_method=S256 state_present=true",
            keycloakProperties.clientId(),
            "openid profile email userId-claim",
            keycloakProperties.redirectUri());

        log.info("OAuth authorize redirect URI = {}", keycloakProperties.redirectUri());
        log.debug("Authorization URL constructed successfully");
        return new AuthorizationRequest(authorizationUrl, state);
    }

    public String consumeCodeVerifier(String state) {
        if (state == null || state.isBlank()) {
            log.warn("consumeCodeVerifier called with null or blank state");
            return null;
        }

        log.info("=== PKCE STATE CONSUMPTION === state_present=true state_length={}", state.length());

        try {
            String codeVerifier = stringRedisTemplate.opsForValue().getAndDelete(keyFor(state));
            if (codeVerifier != null) {
                log.info("=== PKCE CODE VERIFIER RETRIEVED === source=redis length={}", codeVerifier.length());
                return codeVerifier;
            } else {
                log.warn("=== PKCE CODE VERIFIER NOT FOUND IN REDIS === trying in-memory fallback");
            }
        } catch (Exception e) {
            log.warn("Redis unavailable when consuming PKCE state; trying in-memory fallback: {}", e.toString());
        }

        InMemoryEntry entry = inMemoryFallback.remove(keyFor(state));
        if (entry == null) {
            log.warn("=== PKCE CODE VERIFIER NOT FOUND IN MEMORY === state lookup failed");
            return null;
        }
        if (entry.expiryMs < System.currentTimeMillis()) {
            log.warn("=== PKCE CODE VERIFIER EXPIRED === expiry_ms={} current_ms={}", entry.expiryMs, System.currentTimeMillis());
            return null;
        }
        log.info("=== PKCE CODE VERIFIER RETRIEVED === source=in-memory length={}", entry.codeVerifier.length());
        return entry.codeVerifier;
    }

    private String keyFor(String state) {
        return PKCE_STATE_KEY_PREFIX + state;
    }

    public static class AuthorizationRequest {
        private final String authorizationUrl;
        private final String state;

        public AuthorizationRequest(String authorizationUrl, String state) {
            this.authorizationUrl = authorizationUrl;
            this.state = state;
        }

        public String authorizationUrl() { return authorizationUrl; }
        public String state() { return state; }
    }

    private static final class InMemoryEntry {
        final String codeVerifier;
        final long expiryMs;

        InMemoryEntry(String codeVerifier, long expiryMs) {
            this.codeVerifier = codeVerifier;
            this.expiryMs = expiryMs;
        }
    }

}
