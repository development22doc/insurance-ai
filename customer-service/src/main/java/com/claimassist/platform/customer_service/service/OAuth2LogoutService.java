package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.config.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import com.claimassist.platform.customer_service.service.RefreshTokenService;

@Service
@RequiredArgsConstructor
public class OAuth2LogoutService {

    private final RestClient restClient;
    private final KeycloakProperties keycloakProperties;
    private final RefreshTokenService refreshTokenService;

    /**
     * Performs OIDC RP-Initiated Logout using the refresh token.
     */
    public void logout (String refreshToken) {

        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<> ();

        form.add ("client_id", keycloakProperties.clientId ());
        form.add ("client_secret", keycloakProperties.clientSecret ());
        form.add ("refresh_token", refreshToken);

        restClient.post ()
                .uri (keycloakProperties.logoutUri ())
                .contentType (MediaType.APPLICATION_FORM_URLENCODED)
                .body (form)
                .retrieve ()
                .toBodilessEntity ();

        // Revoke locally stored refresh token if present
        try {
            refreshTokenService.revoke(refreshToken);
        } catch (Exception ignored) {
            // best-effort
        }
    }

}
