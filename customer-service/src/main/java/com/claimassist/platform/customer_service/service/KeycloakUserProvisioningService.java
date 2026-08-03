package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.config.KeycloakProperties;
import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to Keycloak's Admin REST API to create the Keycloak-side identity
 * for a new customer, at signup time - this is what replaces "hash the
 * password and INSERT a row" from the custom-JWT implementation.
 * <p>
 * The new Keycloak user is created with the same username/password the
 * customer just submitted (so signup remains a single, synchronous,
 * one-step flow from the client's point of view) and is tagged with a
 * "legacy_user_id" attribute equal to this platform's own numeric customer
 * id - a protocol mapper in realm-export.json copies that attribute into a
 * "userId" claim on every token that client mints, which is what
 * CurrentUserProvider reads platform-wide.
 * <p>
 * Uses a dedicated confidential client (keycloak.admin-client-id /
 * admin-client-secret) whose service account has been granted the
 * realm-management "manage-users" client role - see KEYCLOAK_SETUP.md.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakUserProvisioningService {

    private final KeycloakProperties keycloakProperties;
    private final RestClient restClient = RestClient.create();

    /**
     * @return the Keycloak user id (UUID) of the newly created user.
     */
    public String createUser(String username, String fullName, String password, Long legacyUserId) {
        String adminToken = fetchAdminToken();

        String[] nameParts = fullName.trim().split("\\s+", 2);
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : "";

        Map<String, Object> credential = Map.of(
                "type", "password",
                "value", password,
                "temporary", false
        );

        Map<String, Object> userPayload = new HashMap<>();
        userPayload.put("username", username);
        userPayload.put("email", username);
        userPayload.put("enabled", true);
        userPayload.put("emailVerified", true);
        userPayload.put("firstName", firstName);
        userPayload.put("lastName", lastName);
        userPayload.put("credentials", List.of(credential));
        userPayload.put("attributes", Map.of("legacy_user_id", List.of(String.valueOf(legacyUserId))));

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(keycloakProperties.adminUsersUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .body(userPayload)
                    .retrieve()
                    .toBodilessEntity();

            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            if (location == null) {
                throw new ServiceUnavailableException("Keycloak did not confirm user creation (no Location header)");
            }
            String keycloakUserId = location.substring(location.lastIndexOf('/') + 1);
            log.info("Created Keycloak user. username={} keycloakUserId={}", username, keycloakUserId);
            return keycloakUserId;
        } catch (RestClientException e) {
            log.error("Failed to create Keycloak user for username={}", username, e);
            throw new ServiceUnavailableException("Unable to provision identity provider account. Please try again later.");
        }
    }

    private String fetchAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", keycloakProperties.adminClientId());
        form.add("client_secret", keycloakProperties.adminClientSecret());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(keycloakProperties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("access_token") == null) {
                throw new ServiceUnavailableException("Keycloak did not return an admin access token");
            }
            return (String) response.get("access_token");
        } catch (RestClientException e) {
            log.error("Failed to obtain a Keycloak admin token", e);
            throw new ServiceUnavailableException("Unable to reach identity provider. Please try again later.");
        }
    }
}
