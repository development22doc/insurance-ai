package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.config.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthorizationService {

    private final PkceService pkceService;
    private final KeycloakProperties keycloakProperties;

    /**
     * Temporary PKCE store.
     * Phase 6: Replace with Redis.
     */
    private final Map<String, String> codeVerifierStore =
            new ConcurrentHashMap<> ();

    public AuthorizationRequest createAuthorizationRequest () {

        log.info("Starting createAuthorizationRequest");
        log.debug("KeycloakProperties: {}", keycloakProperties);

        if (keycloakProperties == null) {
            log.error("KeycloakProperties is null!");
            throw new IllegalStateException("KeycloakProperties is not configured");
        }

        String state = pkceService.generateState ();
        log.debug("Generated state: {}", state);

        String codeVerifier = pkceService.generateCodeVerifier ();
        log.debug("Generated codeVerifier length: {}", codeVerifier.length());

        String codeChallenge =
                pkceService.generateCodeChallenge (codeVerifier);
        log.debug("Generated codeChallenge: {}", codeChallenge);

        codeVerifierStore.put (state, codeVerifier);

        String authorizationUri = keycloakProperties.authorizationUri();
        log.debug("Authorization URI: {}", authorizationUri);

        if (authorizationUri == null || authorizationUri.isEmpty()) {
            log.error("Authorization URI is null or empty!");
            log.debug("serverUrl: {}, realm: {}", keycloakProperties.serverUrl(), keycloakProperties.realm());
            throw new IllegalStateException("Authorization URI could not be constructed");
        }

        String authorizationUrl =
                UriComponentsBuilder
                        .fromHttpUrl (authorizationUri)
                        .queryParam ("client_id", keycloakProperties.clientId ())
                        .queryParam ("response_type", "code")
                        .queryParam ("scope", "openid profile email")
                        .queryParam ("redirect_uri", keycloakProperties.redirectUri ())
                        .queryParam ("code_challenge", codeChallenge)
                        .queryParam ("code_challenge_method", "S256")
                        .queryParam ("state", state)
                        .build ()
                        .encode ()
                        .toUriString ();

        log.info("Authorization URL constructed successfully: {}", authorizationUrl);

        return new AuthorizationRequest (
                authorizationUrl,
                state
        );
    }

    public String consumeCodeVerifier (String state) {

        return codeVerifierStore.remove (state);

    }

    public record AuthorizationRequest(
            String authorizationUrl,
            String state
    ) {
    }

}
