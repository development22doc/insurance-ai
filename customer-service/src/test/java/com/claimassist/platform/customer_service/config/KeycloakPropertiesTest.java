package com.claimassist.platform.customer_service.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakPropertiesTest {

    private final KeycloakProperties props = new KeycloakProperties(
            "http://keycloak:8180", "claimassist",
            "app-client", "secret", "http://localhost:8080/callback",
            "admin-client", "admin-secret");

    @Test
    void issuerUri_buildsFromServerAndRealm() {
        assertThat(props.issuerUri()).isEqualTo("http://keycloak:8180/realms/claimassist");
    }

    @Test
    void protocolUris_buildExpectedEndpoints() {
        assertThat(props.authorizationUri())
                .isEqualTo("http://keycloak:8180/realms/claimassist/protocol/openid-connect/auth");
        assertThat(props.tokenUri())
                .isEqualTo("http://keycloak:8180/realms/claimassist/protocol/openid-connect/token");
        assertThat(props.jwksUri())
                .isEqualTo("http://keycloak:8180/realms/claimassist/protocol/openid-connect/certs");
        assertThat(props.logoutUri())
                .isEqualTo("http://keycloak:8180/realms/claimassist/protocol/openid-connect/logout");
    }

    @Test
    void adminUsersUri_buildsAdminPath() {
        assertThat(props.adminUsersUri())
                .isEqualTo("http://keycloak:8180/admin/realms/claimassist/users");
    }
}