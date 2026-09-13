package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.config.KeycloakProperties;
import com.claimassist.platform.customer_service.dto.auth.AuthResponse;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import com.claimassist.platform.customer_service.service.RefreshTokenService;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2TokenService {

    private final RestClient restClient;
    private final KeycloakProperties keycloakProperties;
    private final CustomerRepository customerRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtDecoder jwtDecoder;
    private final EventLogger eventLogger;
    private final PerformanceLogger performanceLogger;

    /**
     * Exchange authorization code for tokens at Keycloak token endpoint.
     */
    public AuthResponse exchangeAuthorizationCode(String code, String codeVerifier) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        form.add("client_id", keycloakProperties.clientId());
        // Only send client_secret if it's non-empty (for confidential clients)
        // Public clients using PKCE do not send client_secret
        boolean clientSecretSent = keycloakProperties.clientSecret() != null && !keycloakProperties.clientSecret().isEmpty();
        if (clientSecretSent) {
            form.add("client_secret", keycloakProperties.clientSecret());
        }
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", keycloakProperties.redirectUri());
        form.add("code_verifier", codeVerifier);
        form.add("scope", "openid profile email userId-claim");

        // Safe diagnostics: log request metadata (no secrets)
        log.info("=== TOKEN EXCHANGE REQUEST === client_id={} grant_type={} redirect_uri={} code_present={} code_verifier_present={} client_secret_sent={} scope={}",
            keycloakProperties.clientId(),
            "authorization_code",
            keycloakProperties.redirectUri(),
            code != null && !code.isEmpty(),
            codeVerifier != null && !codeVerifier.isEmpty(),
            clientSecretSent,
            "openid profile email userId-claim");

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(keycloakProperties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            log.info("=== KEYCLOAK HTTP STATUS === 200_OK");
        } catch (Exception e) {
            log.error("=== KEYCLOAK TOKEN EXCHANGE FAILED === exception_type={} message={}", e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }

        // Log token response diagnostics (claim presence only, no sensitive values)
        log.info("=== KEYCLOAK RESPONSE FIELDS === access_token_present={} refresh_token_present={} id_token_present={} token_type_present={} expires_in_present={} scope_present={}",
            response.containsKey("access_token"),
            response.containsKey("refresh_token"),
            response.containsKey("id_token"),
            response.containsKey("token_type"),
            response.containsKey("expires_in"),
            response.containsKey("scope"));

        String accessToken = (String) response.get("access_token");
        String idToken = (String) response.get("id_token");

        // Decode and analyze access_token
        boolean accessTokenHasSub = false;
        boolean accessTokenHasUserId = false;
        if (accessToken != null) {
            try {
                Jwt jwt = jwtDecoder.decode(accessToken);
                accessTokenHasSub = jwt.getClaimAsString("sub") != null;
                accessTokenHasUserId = jwt.getClaim("userId") != null;
                log.info("=== ACCESS TOKEN CLAIMS DIAGNOSTICS === sub_present={} preferred_username_present={} userId_present={} email_present={}",
                    accessTokenHasSub,
                    jwt.getClaimAsString("preferred_username") != null,
                    accessTokenHasUserId,
                    jwt.getClaimAsString("email") != null);
            } catch (Exception e) {
                log.warn("Failed to decode access token for diagnostics: {}", e.getMessage());
            }
        }

        // Decode and analyze id_token if present
        boolean idTokenHasSub = false;
        boolean idTokenHasUserId = false;
        if (idToken != null) {
            try {
                Jwt idJwt = jwtDecoder.decode(idToken);
                idTokenHasSub = idJwt.getClaimAsString("sub") != null;
                idTokenHasUserId = idJwt.getClaim("userId") != null;
                log.info("=== ID TOKEN CLAIMS DIAGNOSTICS === sub_present={} preferred_username_present={} userId_present={} email_present={}",
                    idTokenHasSub,
                    idJwt.getClaimAsString("preferred_username") != null,
                    idTokenHasUserId,
                    idJwt.getClaimAsString("email") != null);
            } catch (Exception e) {
                log.warn("Failed to decode id_token for diagnostics: {}", e.getMessage());
            }
        }

        // Log which token is being used as CLAIMASSIST_ACCESS_TOKEN
        log.info("=== CLAIMASSIST_ACCESS_TOKEN SOURCE === source=access_token access_token_present={}", accessToken != null);

        AuthResponse authResponse = buildAuthResponseFromTokenResponse(response);

        // Final verification: log what was actually returned
        log.info("=== AUTH RESPONSE CONSTRUCTION === access_token_set={} id_token_set={} customerId_set={}",
            authResponse.accessToken() != null,
            authResponse.idToken() != null,
            authResponse.customerId() != null);

        return authResponse;
    }

    /**
     * Refresh access/refresh tokens using the provided refresh token.
     */
    public AuthResponse refreshToken(String oldRefreshToken) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        form.add("client_id", keycloakProperties.clientId());
        // Only send client_secret if it's non-empty (for confidential clients)
        // Public clients using PKCE do not send client_secret
        if (keycloakProperties.clientSecret() != null && !keycloakProperties.clientSecret().isEmpty()) {
            form.add("client_secret", keycloakProperties.clientSecret());
        }
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", oldRefreshToken);
        form.add("scope", "openid profile email userId-claim");

        Map<String, Object> response = restClient.post()
                .uri(keycloakProperties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        // Extract id_token to validate and resolve username for local rotation/lookup
        String idToken = (String) response.get("id_token");
        String preferredUsername = null;
        if (idToken != null) {
            try {
                Jwt idJwt = jwtDecoder.decode(idToken);
                preferredUsername = idJwt.getClaimAsString("preferred_username");
            } catch (Exception ignore) {
                // best-effort: if id_token cannot be decoded, continue without customer lookup
            }
        }

        Optional<Customer> customerOpt = Optional.empty();
        if (preferredUsername != null) {
            customerOpt = customerRepository.findByUsername(preferredUsername);
        }

        // If we have a local customer record, perform local refresh-token rotation as an extra guard
        String newRefreshToken = (String) response.get("refresh_token");
        if (customerOpt.isPresent() && newRefreshToken != null) {
            // apply rotation; allow the RefreshTokenService to throw BadRequestException when local guard rejects
            refreshTokenService.rotateIfPresent(oldRefreshToken, newRefreshToken, Instant.now(), null);
        }

        AuthResponse ar = buildAuthResponseFromTokenResponse(response);

        if (customerOpt.isPresent()) {
            Customer c = customerOpt.get();
            return new AuthResponse(ar.accessToken(), ar.refreshToken(), ar.tokenType(), ar.expiresIn(), ar.refreshExpiresIn(), ar.scope(), ar.idToken(), c.getId(), c.getFullName());
        }

        return ar;
    }

    private AuthResponse buildAuthResponseFromTokenResponse(Map<String, Object> response) {
        String accessToken = (String) response.get("access_token");
        String refreshToken = (String) response.get("refresh_token");
        String tokenType = (String) response.get("token_type");
        Long expiresIn = response.get("expires_in") instanceof Number ? ((Number) response.get("expires_in")).longValue() : null;
        Long refreshExpiresIn = response.get("refresh_expires_in") instanceof Number ? ((Number) response.get("refresh_expires_in")).longValue() : null;
        String scope = (String) response.get("scope");
        String idToken = (String) response.get("id_token");

        return new AuthResponse(accessToken, refreshToken, tokenType, expiresIn, refreshExpiresIn, scope, idToken, null, null);
    }

}
