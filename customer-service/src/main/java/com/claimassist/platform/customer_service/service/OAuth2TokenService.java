package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.config.KeycloakProperties;
import com.claimassist.platform.customer_service.dto.auth.AuthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OAuth2TokenService {

    private final RestClient restClient;
    private final KeycloakProperties keycloakProperties;

    public AuthResponse exchangeAuthorizationCode (
            String authorizationCode,
            String codeVerifier) {

        LinkedMultiValueMap<String, String> body =
                new LinkedMultiValueMap<> ();

        body.add ("grant_type", "authorization_code");
        body.add ("client_id", keycloakProperties.clientId ());
        body.add ("client_secret", keycloakProperties.clientSecret ());
        body.add ("code", authorizationCode);
        body.add ("code_verifier", codeVerifier);
        body.add ("redirect_uri", keycloakProperties.redirectUri ());

        Map<String, Object> response =
                restClient.post ()
                        .uri (keycloakProperties.tokenUri ())
                        .contentType (MediaType.APPLICATION_FORM_URLENCODED)
                        .body (body)
                        .retrieve ()
                        .body (Map.class);

        return new AuthResponse (
                (String) response.get ("access_token"),
                (String) response.get ("refresh_token"),
                (String) response.get ("token_type"),
                ((Number) response.get ("expires_in")).longValue (),
                ((Number) response.get ("refresh_expires_in")).longValue (),
                (String) response.get ("scope"),
                (String) response.get ("id_token"),
                null,
                null
        );
    }

    public AuthResponse refreshToken (String refreshToken) {

        LinkedMultiValueMap<String, String> body =
                new LinkedMultiValueMap<> ();

        body.add ("grant_type", "refresh_token");
        body.add ("client_id", keycloakProperties.clientId ());
        body.add ("client_secret", keycloakProperties.clientSecret ());
        body.add ("refresh_token", refreshToken);

        Map<String, Object> response =
                restClient.post ()
                        .uri (keycloakProperties.tokenUri ())
                        .contentType (MediaType.APPLICATION_FORM_URLENCODED)
                        .body (body)
                        .retrieve ()
                        .body (Map.class);

        return new AuthResponse (
                (String) response.get ("access_token"),
                (String) response.get ("refresh_token"),
                (String) response.get ("token_type"),
                ((Number) response.get ("expires_in")).longValue (),
                ((Number) response.get ("refresh_expires_in")).longValue (),
                (String) response.get ("scope"),
                (String) response.get ("id_token"),
                null,
                null
        );
    }

    public void logout (String refreshToken) {

        LinkedMultiValueMap<String, String> body =
                new LinkedMultiValueMap<> ();

        body.add ("client_id", keycloakProperties.clientId ());
        body.add ("client_secret", keycloakProperties.clientSecret ());
        body.add ("refresh_token", refreshToken);

        restClient.post ()
                .uri (keycloakProperties.logoutUri ())
                .contentType (MediaType.APPLICATION_FORM_URLENCODED)
                .body (body)
                .retrieve ()
                .toBodilessEntity ();
    }
}