package com.claimassist.platform.customer_service.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties (prefix = "keycloak")
public record KeycloakProperties(

        String serverUrl,

        String realm,

        /*
         * OAuth2 Client (Authorization Code + PKCE)
         */
        String clientId,

        String clientSecret,

        /*
         * Redirect URI registered in Keycloak
         */
        String redirectUri,

        /*
         * Admin client used ONLY for provisioning users.
         */
        String adminClientId,

        @NotBlank(message = "keycloak.admin-client-secret must be configured via KEYCLOAK_ADMIN_CLIENT_SECRET or SERVICE_CLIENT_SECRET")
        String adminClientSecret

) {

    public String issuerUri () {
        return serverUrl + "/realms/" + realm;
    }

    public String authorizationUri () {
        return issuerUri ()
                + "/protocol/openid-connect/auth";
    }

    public String tokenUri () {
        return issuerUri ()
                + "/protocol/openid-connect/token";
    }

    public String jwksUri () {
        return issuerUri ()
                + "/protocol/openid-connect/certs";
    }

    public String logoutUri () {
        return issuerUri ()
                + "/protocol/openid-connect/logout";
    }

    public String adminUsersUri () {
        return serverUrl
                + "/admin/realms/"
                + realm
                + "/users";
    }

}