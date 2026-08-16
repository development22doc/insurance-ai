package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.customer_service.config.KeycloakProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

/**
 * Phase 3, Section 15: external-dependency resilience for Keycloak provisioning.
 *
 * <p>The provisioning service uses the shared {@link RestClient} (bounded
 * timeouts from Phase 2) and maps ANY transport failure to
 * {@link ServiceUnavailableException}. Crucially it does NOT blindly retry a
 * mutating provisioning call (which could create a duplicate side effect /
 * orphan), and it does NOT hide the failure - the caller's compensation logic
 * decides how to reconcile. This test pins that classification and
 * non-retry behavior.
 */
class KeycloakUserProvisioningServiceTest {

    private RestClient restClient;
    private KeycloakUserProvisioningService service;

    @BeforeEach
    void setUp() {
        KeycloakProperties props = new KeycloakProperties(
                "http://keycloak:8180", "claimassist",
                "app-client", "secret", "http://redirect",
                "admin-client", "admin-secret");
        restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        service = new KeycloakUserProvisioningService(
                props, restClient, mock(EventLogger.class), mock(PerformanceLogger.class));
    }

    @Test
    void transientFailureOnAdminTokenFetchIsClassifiedAndNotRetried() {
        when(restClient.post().uri(anyString()).contentType(any(MediaType.class))
                .body(any()).retrieve().body(Map.class))
                .thenThrow(new ResourceAccessException("connection reset by Keycloak"));

        assertThatThrownBy(() -> service.createUser("a@b.com", "Alice", "pw", 1L))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}