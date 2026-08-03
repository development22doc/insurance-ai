package com.claimassist.platform.customer_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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