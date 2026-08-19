package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.customer_service.config.KeycloakProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KeycloakUserProvisioningServiceTest {

    private KeycloakUserProvisioningService service(String adminClientId, String adminClientSecret) {
        KeycloakProperties props = new KeycloakProperties(
                "https://kc.example.com", "demo-realm", "client", "secret",
                "https://app/redirect", adminClientId, adminClientSecret);
        return new KeycloakUserProvisioningService(props, mock(RestClient.class),
                mock(EventLogger.class), mock(PerformanceLogger.class));
    }

    @Test
    void buildAdminTokenRequestFormUsesConfiguredClient() {
        KeycloakUserProvisioningService s = service("admin-client", "admin-secret");
        MultiValueMap<String, String> form = s.buildAdminTokenRequestForm();
        assertThat(form.getFirst("grant_type")).isEqualTo("client_credentials");
        assertThat(form.getFirst("client_id")).isEqualTo("admin-client");
        assertThat(form.getFirst("client_secret")).isEqualTo("admin-secret");
    }

    @Test
    void deleteUserReturnsEarlyWhenIdNull() {
        KeycloakUserProvisioningService s = service("a", "b");
        s.deleteUser(null);
        s.deleteUser("   ");
    }

    @Test
    void updateUserReturnsEarlyWhenIdBlank() {
        KeycloakUserProvisioningService s = service("a", "b");
        s.updateUser(null, "Alice A", "alice@example.com");
        s.updateUser("", "Alice A", "alice@example.com");
    }
}
