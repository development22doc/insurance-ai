package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.customer_service.config.KeycloakProperties;
import com.claimassist.platform.customer_service.dto.auth.AuthResponse;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
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
    private final EventLogger eventLogger;
    private final PerformanceLogger performanceLogger;

    public AuthResponse exchangeAuthorizationCode(
            String authorizationCode,
            String codeVerifier) {

        LinkedMultiValueMap<String, String> body =
                new LinkedMultiValueMap<>();

        body.add("grant_type", "authorization_code");
        body.add("client_id", keycloakProperties.clientId());
        body.add("client_secret", keycloakProperties.clientSecret());
        body.add("code", authorizationCode);
        body.add("code_verifier", codeVerifier);
        body.add("redirect_uri", keycloakProperties.redirectUri());

        Map<String, Object> response =
                restClient.post()
                        .uri(keycloakProperties.tokenUri())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(body)
                        .retrieve()
                        .body(Map.class);

        String idToken = (String) response.get("id_token");
        String username = extractUsernameFromValidatedIdToken(idToken);

        Long customerId = null;
        String fullName = null;
        String refreshToken = (String) response.get("refresh_token");

        if (username != null) {
            long dbStart = System.currentTimeMillis();
            Customer customer = customerRepository.findByUsername(username).orElse(null);
            long dbDuration = System.currentTimeMillis() - dbStart;
            Map<String, Object> dbDetails = new java.util.HashMap<>();
            dbDetails.put("username", username);
            dbDetails.put("event", customer != null ? "CUSTOMER_FOUND" : "CUSTOMER_NOT_FOUND");
            dbDetails.put("executionTimeMs", dbDuration);
            dbDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            eventLogger.logDatabaseEvent("customer-service", "customer-service", dbDuration, dbDetails);
            performanceLogger.log("REPOSITORY", "repository.customer.find", dbDuration,
                    Map.of("username", username, "found", customer != null));

            if (customer != null) {
                customerId = customer.getId();
                fullName = customer.getFullName();

                // Persist the ACTUAL Keycloak refresh token (the value the client
                // will present on refresh/logout), so local validation/rotation/
                // revocation can match it.
                if (refreshToken != null) {
                    Instant now = Instant.now();
                    refreshTokenService.createRefreshToken(
                            customer, refreshToken, now, expiresAtFrom(response, now));
                }
            }
        }

        return new AuthResponse(
                (String) response.get("access_token"),
                refreshToken,
                (String) response.get("token_type"),
                ((Number) response.get("expires_in")).longValue(),
                ((Number) response.get("refresh_expires_in")).longValue(),
                (String) response.get("scope"),
                idToken,
                customerId,
                fullName
        );
    }

    public AuthResponse refreshToken(String refreshToken) {

        // SAFE ORDERING (Phase 2): Keycloak is authoritative for refresh-token
        // validation and rotation, so the external Keycloak call happens FIRST.
        // Only AFTER it succeeds do we do the atomic local persistence/rotation -
        // never inside a DB transaction, and never a local rotate that could
        // consume a token Keycloak rejected.
        LinkedMultiValueMap<String, String> body =
                new LinkedMultiValueMap<>();

        body.add("grant_type", "refresh_token");
        body.add("client_id", keycloakProperties.clientId());
        body.add("client_secret", keycloakProperties.clientSecret());
        body.add("refresh_token", refreshToken);

        // Keycloak throws 400 (invalid_grant) for an expired/revoked/used token;
        // that propagates to the global error handler as a rejected refresh.
        Map<String, Object> response =
                restClient.post()
                        .uri(keycloakProperties.tokenUri())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(body)
                        .retrieve()
                        .body(Map.class);

        String idToken = (String) response.get("id_token");
        String newRefreshToken = (String) response.get("refresh_token");
        String username = extractUsernameFromValidatedIdToken(idToken);

        Long customerId = null;
        String fullName = null;

        if (username != null) {
            long dbStart = System.currentTimeMillis();
            Customer customer = customerRepository.findByUsername(username).orElse(null);
            long dbDuration = System.currentTimeMillis() - dbStart;
            Map<String, Object> dbDetails = new java.util.HashMap<>();
            dbDetails.put("username", username);
            dbDetails.put("event", customer != null ? "CUSTOMER_FOUND" : "CUSTOMER_NOT_FOUND");
            dbDetails.put("executionTimeMs", dbDuration);
            dbDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            eventLogger.logDatabaseEvent("customer-service", "customer-service", dbDuration, dbDetails);
            performanceLogger.log("REPOSITORY", "repository.customer.find", dbDuration,
                    Map.of("username", username, "found", customer != null));

            if (customer != null) {
                customerId = customer.getId();
                fullName = customer.getFullName();
            }
        }

        // Atomic local rotation to the NEW Keycloak token (single-use guard on
        // the old token). Empty result = no local record; Keycloak is
        // authoritative and the refresh stands. Revoked/expired/used local
        // records throw here as a defense-in-depth replay/expiry guard.
        if (customerId != null && newRefreshToken != null && !newRefreshToken.equals(refreshToken)) {
            Instant now = Instant.now();
            refreshTokenService.rotateIfPresent(refreshToken, newRefreshToken, now, expiresAtFrom(response, now));
        }

        return new AuthResponse(
                (String) response.get("access_token"),
                newRefreshToken,
                (String) response.get("token_type"),
                ((Number) response.get("expires_in")).longValue(),
                ((Number) response.get("refresh_expires_in")).longValue(),
                (String) response.get("scope"),
                idToken,
                customerId,
                fullName
        );
    }

    private Instant expiresAtFrom(Map<String, Object> response, Instant now) {
        Object re = response.get("refresh_expires_in");
        if (re instanceof Number n) {
            long secs = n.longValue();
            if (secs > 0) {
                return now.plusSeconds(secs);
            }
        }
        return null; // RefreshTokenService applies its configured TTL fallback
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
            Jwt jwt = jwtDecoder.decode(idToken);

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