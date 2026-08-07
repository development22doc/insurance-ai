package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.config.KeycloakProperties;
import com.claimassist.platform.customer_service.dto.auth.AuthResponse;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j

@Service
@RequiredArgsConstructor
public class OAuth2TokenService {

    private final RestClient restClient;
    private final KeycloakProperties keycloakProperties;
    private final CustomerRepository customerRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtDecoder jwtDecoder;

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

        String idToken = (String) response.get ("id_token");
        String username = extractUsernameFromValidatedIdToken(idToken);

        Long customerId = null;
        String fullName = null;

        if (username != null) {
            Customer customer = customerRepository.findByUsername(username).orElse(null);
            if (customer != null) {
                customerId = customer.getId ();
                fullName = customer.getFullName ();

                // persist refresh token (rotation handled later on refresh)
                String refreshToken = (String) response.get("refresh_token");
                if (refreshToken != null) {
                    // create local token record (use Keycloak token string as stored token)
                    // We will treat Keycloak refresh token directly as our stored token value
                    // but also create a local generated token for rotation if needed.
                    refreshTokenService.createRefreshToken(customer);
                }
            }
        }

        return new AuthResponse (
                (String) response.get ("access_token"),
                (String) response.get ("refresh_token"),
                (String) response.get ("token_type"),
                ((Number) response.get ("expires_in")).longValue (),
                ((Number) response.get ("refresh_expires_in")).longValue (),
                (String) response.get ("scope"),
                idToken,
                customerId,
                fullName
        );
    }

    public AuthResponse refreshToken (String refreshToken) {

        // Validate locally that refresh token exists and is not expired/revoked
        try {
            // validate and rotate locally - returns a newly created local token record
            refreshTokenService.validateAndRotate(refreshToken);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

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

        String idToken = (String) response.get ("id_token");
        String username = extractUsernameFromValidatedIdToken(idToken);

        Long customerId = null;
        String fullName = null;

        if (username != null) {
            Customer customer = customerRepository.findByUsername(username).orElse(null);
            if (customer != null) {
                customerId = customer.getId ();
                fullName = customer.getFullName ();
            }
        }

        // If Keycloak returned a new refresh token, we should record it (rotation)
        if (response.containsKey("refresh_token")) {
            // use local rotation: create a new record for the customer
            if (customerId != null) {
                customerRepository.findById(customerId).ifPresent(c -> refreshTokenService.createRefreshToken(c));
            }
        }

        return new AuthResponse (
                (String) response.get ("access_token"),
                (String) response.get ("refresh_token"),
                (String) response.get ("token_type"),
                ((Number) response.get ("expires_in")).longValue (),
                ((Number) response.get ("refresh_expires_in")).longValue (),
                (String) response.get ("scope"),
                idToken,
                customerId,
                fullName
        );
    }

    /**
     * Validates the ID token's JWT signature using the Keycloak JWKS endpoint
     * and extracts the preferred_username claim (or email as fallback).
     */
    private String extractUsernameFromValidatedIdToken(String idToken) {
        if (idToken == null) {
            log.warn("ID token is null");
            return null;
        }

        try {
            // Decode and validate JWT signature using JwtDecoder configured with JWKS
            Jwt jwt = jwtDecoder.decode(idToken);

            // Extract preferred_username or email claim
            String preferred = jwt.getClaimAsString("preferred_username");
            if (preferred == null) {
                preferred = jwt.getClaimAsString("email");
            }

            if (preferred == null) {
                log.warn("No preferred_username or email claim found in validated ID token");
            }

            return preferred;
        } catch (JwtException e) {
            log.warn("JWT signature validation failed for ID token");
            return null;
        } catch (Exception e) {
            log.warn("Unexpected error decoding ID token");
            return null;
        }
    }


}
