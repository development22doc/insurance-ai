package com.claimassist.platform.common_lib.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserProviderTest {

    private final CurrentUserProvider provider = new CurrentUserProvider();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Jwt userJwt(Long userId, String username) {
        return Jwt.withTokenValue("user.token." + userId)
                .header("alg", "none")
                .claim("userId", userId)
                .claim("preferred_username", username)
                .claim("azp", "claimassist-customer-app")
                .build();
    }

    private Jwt serviceJwt(String clientId) {
        return Jwt.withTokenValue("service.token")
                .header("alg", "none")
                .claim("azp", clientId)
                .claim("client_id", clientId)
                .build();
    }

    private void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of()));
    }

    @Test
    void getCurrentUserIdReturnsNumericUserIdFromUserToken() {
        authenticate(userJwt(42L, "demo.customer"));
        assertThat(provider.getCurrentUserId()).isEqualTo(42L);
    }

    @Test
    void callerTypeIsUserForUserIdBearingToken() {
        authenticate(userJwt(7L, "demo.customer"));
        assertThat(provider.callerType()).isEqualTo(CallerType.USER);
        assertThat(provider.isUserToken()).isTrue();
        assertThat(provider.isServiceToken()).isFalse();
    }

    @Test
    void callerTypeIsServiceForClientCredentialsTokenWithoutUserId() {
        authenticate(serviceJwt("claimassist-admin-service"));
        assertThat(provider.callerType()).isEqualTo(CallerType.SERVICE);
        assertThat(provider.isServiceToken()).isTrue();
        assertThat(provider.isUserToken()).isFalse();
    }

    @Test
    void getServiceClientIdReturnsClientIdForServiceToken() {
        authenticate(serviceJwt("claimassist-admin-service"));
        assertThat(provider.getServiceClientId()).isEqualTo("claimassist-admin-service");
    }

    @Test
    void getServiceClientIdIsNullForUserToken() {
        authenticate(userJwt(7L, "demo.customer"));
        assertThat(provider.getServiceClientId()).isNull();
    }

    @Test
    void getCurrentUserIdFailsClosedForServiceToken() {
        authenticate(serviceJwt("claimassist-admin-service"));
        assertThatThrownBy(() -> provider.getCurrentUserId())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageContaining("service (client-credentials) token");
    }

    @Test
    void getCurrentUserIdThrowsWhenUserIdClaimMissing() {
        // A token with azp but no userId is a SERVICE token - fail closed.
        authenticate(serviceJwt("claimassist-admin-service"));
        assertThatThrownBy(() -> provider.getCurrentUserId())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageContaining("service (client-credentials) token");
    }

    @Test
    void getCurrentJwtThrowsWhenNoAuthentication() {
        assertThatThrownBy(() -> provider.getCurrentJwt())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageContaining("No authenticated Keycloak JWT found");
    }

    @Test
    void classifyUserTokenWithoutContext() {
        assertThat(CurrentUserProvider.classify(userJwt(1L, "u"))).isEqualTo(CallerType.USER);
    }

    @Test
    void classifyServiceTokenWithoutContext() {
        assertThat(CurrentUserProvider.classify(serviceJwt("svc"))).isEqualTo(CallerType.SERVICE);
    }

    @Test
    void classifyThrowsWhenNeitherUserNorServiceIdentityPresent() {
        Jwt ambiguous = Jwt.withTokenValue("ambiguous")
                .header("alg", "none")
                .claim("sub", "uuid")
                .build();
        assertThatThrownBy(() -> CurrentUserProvider.classify(ambiguous))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageContaining("Cannot classify JWT");
    }
}