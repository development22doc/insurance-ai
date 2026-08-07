package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.config.KeycloakProperties;
import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
    private final EventLogger eventLogger;

    /**
     * @return the Keycloak user id (UUID) of the newly created user.
     */
    public String createUser(String username, String fullName, String password, Long legacyUserId) {
        long startTime = System.currentTimeMillis();
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
            long apiStartTime = System.currentTimeMillis();
            Map<String, Object> apiStartDetails = new HashMap<>();
            apiStartDetails.put("event", "KEYCLOAK_REQUEST_STARTED");
            apiStartDetails.put("operation", "create_user");
            apiStartDetails.put("username", username);
            apiStartDetails.put("customerId", legacyUserId);
            eventLogger.logBusinessEvent("customer-service", "customer-service", apiStartDetails);

            ResponseEntity<Void> response = restClient.post()
                    .uri(keycloakProperties.adminUsersUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .body(userPayload)
                    .retrieve()
                    .toBodilessEntity();

            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            if (location == null) {
                long apiDuration = System.currentTimeMillis() - apiStartTime;
                Map<String, Object> failDetails = new HashMap<>();
                failDetails.put("event", "KEYCLOAK_REQUEST_FAILED");
                failDetails.put("operation", "create_user");
                failDetails.put("username", username);
                failDetails.put("reason", "No Location header in response");
                failDetails.put("executionTimeMs", apiDuration);
                failDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
                eventLogger.logSecurityEvent("customer-service", "customer-service", failDetails);

                throw new ServiceUnavailableException("Keycloak did not confirm user creation (no Location header)");
            }
            String keycloakUserId = location.substring(location.lastIndexOf('/') + 1);

            long apiDuration = System.currentTimeMillis() - apiStartTime;
            Map<String, Object> apiCompleteDetails = new HashMap<>();
            apiCompleteDetails.put("event", "KEYCLOAK_REQUEST_COMPLETED");
            apiCompleteDetails.put("operation", "create_user");
            apiCompleteDetails.put("username", username);
            apiCompleteDetails.put("keycloakUserId", keycloakUserId);
            apiCompleteDetails.put("executionTimeMs", apiDuration);
            eventLogger.logBusinessEvent("customer-service", "customer-service", apiCompleteDetails);

            long totalDuration = System.currentTimeMillis() - startTime;
            Map<String, Object> userCreatedDetails = new HashMap<>();
            userCreatedDetails.put("event", "KEYCLOAK_USER_CREATED");
            userCreatedDetails.put("username", username);
            userCreatedDetails.put("keycloakUserId", keycloakUserId);
            userCreatedDetails.put("customerId", legacyUserId);
            userCreatedDetails.put("executionTimeMs", totalDuration);
            eventLogger.logBusinessEvent("customer-service", "customer-service", userCreatedDetails);

            return keycloakUserId;

        } catch (RestClientException e) {
            long duration = System.currentTimeMillis() - startTime;
            Map<String, Object> failDetails = new HashMap<>();
            failDetails.put("event", "KEYCLOAK_REQUEST_FAILED");
            failDetails.put("operation", "create_user");
            failDetails.put("username", username);
            failDetails.put("customerId", legacyUserId);
            failDetails.put("reason", e.getClass().getSimpleName());
            failDetails.put("executionTimeMs", duration);
            failDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            eventLogger.logSecurityEvent("customer-service", "customer-service", failDetails);

            throw new ServiceUnavailableException("Unable to provision identity provider account. Please try again later.");
        }
    }

    private String fetchAdminToken() {
        long startTime = System.currentTimeMillis();
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
                long duration = System.currentTimeMillis() - startTime;
                Map<String, Object> failDetails = new HashMap<>();
                failDetails.put("event", "KEYCLOAK_REQUEST_FAILED");
                failDetails.put("operation", "fetch_admin_token");
                failDetails.put("reason", "No access_token in response");
                failDetails.put("executionTimeMs", duration);
                failDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
                eventLogger.logSecurityEvent("customer-service", "customer-service", failDetails);

                throw new ServiceUnavailableException("Keycloak did not return an admin access token");
            }

            long duration = System.currentTimeMillis() - startTime;
            Map<String, Object> completeDetails = new HashMap<>();
            completeDetails.put("event", "KEYCLOAK_REQUEST_COMPLETED");
            completeDetails.put("operation", "fetch_admin_token");
            completeDetails.put("executionTimeMs", duration);
            eventLogger.logBusinessEvent("customer-service", "customer-service", completeDetails);

            return (String) response.get("access_token");

        } catch (RestClientException e) {
            long duration = System.currentTimeMillis() - startTime;
            Map<String, Object> failDetails = new HashMap<>();
            failDetails.put("event", "KEYCLOAK_REQUEST_FAILED");
            failDetails.put("operation", "fetch_admin_token");
            failDetails.put("reason", e.getClass().getSimpleName());
            failDetails.put("executionTimeMs", duration);
            failDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            eventLogger.logSecurityEvent("customer-service", "customer-service", failDetails);

            throw new ServiceUnavailableException("Unable to reach identity provider. Please try again later.");
        }
    }
}
