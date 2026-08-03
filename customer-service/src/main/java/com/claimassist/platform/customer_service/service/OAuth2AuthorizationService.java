package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.config.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
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

        String state = pkceService.generateState ();

        String codeVerifier = pkceService.generateCodeVerifier ();

        String codeChallenge =
                pkceService.generateCodeChallenge (codeVerifier);

        codeVerifierStore.put (state, codeVerifier);

        String authorizationUrl =
                UriComponentsBuilder
                        .fromHttpUrl (keycloakProperties.authorizationUri ())
                        .queryParam ("client_id", keycloakProperties.clientId ())
                        .queryParam ("response_type", "code")
                        .queryParam ("scope", "openid profile email")
                        .queryParam ("redirect_uri", keycloakProperties.redirectUri ())
                        .queryParam ("code_challenge", codeChallenge)
                        .queryParam ("code_challenge_method", "S256")
                        .queryParam ("state", state)
                        .build ()
                        .toUriString ();

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