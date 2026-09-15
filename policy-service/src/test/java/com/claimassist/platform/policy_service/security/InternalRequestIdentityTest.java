package com.claimassist.platform.policy_service.security;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.security.CallerType;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalRequestIdentityTest {

    private CurrentUserProvider currentUserProvider;
    private InternalRequestIdentity internalRequestIdentity;

    @BeforeEach
    void setUp() {
        currentUserProvider = mock(CurrentUserProvider.class);
        internalRequestIdentity = new InternalRequestIdentity(currentUserProvider);
        ReflectionTestUtils.setField(internalRequestIdentity, "trustedServiceClientIds", "claimassist-admin-service,claims-service");
    }

    @Test
    void userTokenUsesJwtUserId() {
        Jwt userJwt = Jwt.withTokenValue("user-token")
                .header("alg", "none")
                .claim("userId", 42L)
                .claim("preferred_username", "user42")
                .build();
        when(currentUserProvider.getCurrentJwt()).thenReturn(userJwt);
        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);

        Long userId = internalRequestIdentity.resolveCallingUserId("999");

        assertThat(userId).isEqualTo(42L);
    }

    @Test
    void trustedServiceTokenWithValidXUserId() {
        Jwt serviceJwt = Jwt.withTokenValue("service-token")
                .header("alg", "none")
                .claim("azp", "claims-service")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(currentUserProvider.getCurrentJwt()).thenReturn(serviceJwt);
        when(currentUserProvider.getServiceClientId()).thenReturn("claims-service");

        Long userId = internalRequestIdentity.resolveCallingUserId("77");

        assertThat(userId).isEqualTo(77L);
    }

    @Test
    void untrustedServiceTokenThrowsAccessDenied() {
        Jwt serviceJwt = Jwt.withTokenValue("service-token")
                .header("alg", "none")
                .claim("azp", "unknown-service")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(currentUserProvider.getCurrentJwt()).thenReturn(serviceJwt);
        when(currentUserProvider.getServiceClientId()).thenReturn("unknown-service");

        assertThatThrownBy(() -> internalRequestIdentity.resolveCallingUserId("77"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Untrusted service caller");
    }

    @Test
    void serviceTokenWithoutXUserIdThrowsBadRequest() {
        Jwt serviceJwt = Jwt.withTokenValue("service-token")
                .header("alg", "none")
                .claim("azp", "claims-service")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(currentUserProvider.getCurrentJwt()).thenReturn(serviceJwt);
        when(currentUserProvider.getServiceClientId()).thenReturn("claims-service");

        assertThatThrownBy(() -> internalRequestIdentity.resolveCallingUserId(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Missing X-User-Id");
    }

    @Test
    void serviceTokenWithInvalidXUserIdThrowsBadRequest() {
        Jwt serviceJwt = Jwt.withTokenValue("service-token")
                .header("alg", "none")
                .claim("azp", "claims-service")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(currentUserProvider.getCurrentJwt()).thenReturn(serviceJwt);
        when(currentUserProvider.getServiceClientId()).thenReturn("claims-service");

        assertThatThrownBy(() -> internalRequestIdentity.resolveCallingUserId("not-a-number"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid X-User-Id");
    }

    @Test
    void emptyTrustedServiceListRejectsAllServices() {
        ReflectionTestUtils.setField(internalRequestIdentity, "trustedServiceClientIds", "");
        Jwt serviceJwt = Jwt.withTokenValue("service-token")
                .header("alg", "none")
                .claim("azp", "claims-service")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(currentUserProvider.getCurrentJwt()).thenReturn(serviceJwt);
        when(currentUserProvider.getServiceClientId()).thenReturn("claims-service");

        assertThatThrownBy(() -> internalRequestIdentity.resolveCallingUserId("77"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Untrusted service caller");
    }
}
