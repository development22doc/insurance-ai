package com.claimassist.platform.customer_service.security;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalRequestIdentityTest {

    private CurrentUserProvider currentUserProvider;
    private InternalRequestIdentity identity;

    @BeforeEach
    void setUp() {
        currentUserProvider = mock(CurrentUserProvider.class);
        identity = new InternalRequestIdentity(currentUserProvider);
    }

    private Jwt userJwt() {
        return Jwt.withTokenValue("u").claim("userId", 42).header("alg", "none").build();
    }

    private Jwt serviceJwt() {
        return Jwt.withTokenValue("s").claim("azp", "claims-service").header("alg", "none").build();
    }

    @Test
    void userToken_usesVerifiedUserId_ignoresXUserIdHeader() {
        when(currentUserProvider.getCurrentJwt()).thenReturn(userJwt());
        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);

        Long result = identity.resolveCallingUserId("999");

        assertThat(result).isEqualTo(42L);
    }

    @Test
    void trustedService_parsesXUserId() {
        ReflectionTestUtils.setField(identity, "trustedServiceClientIds", "claims-service,agent-service");
        when(currentUserProvider.getCurrentJwt()).thenReturn(serviceJwt());
        when(currentUserProvider.getServiceClientId()).thenReturn("claims-service");

        Long result = identity.resolveCallingUserId("  7 ");

        assertThat(result).isEqualTo(7L);
    }

    @Test
    void untrustedService_throwsAccessDenied() {
        ReflectionTestUtils.setField(identity, "trustedServiceClientIds", "claims-service,agent-service");
        when(currentUserProvider.getCurrentJwt()).thenReturn(serviceJwt());
        when(currentUserProvider.getServiceClientId()).thenReturn("evil-service");

        assertThatThrownBy(() -> identity.resolveCallingUserId("7"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void serviceWithoutAllowlist_throwsAccessDenied() {
        when(currentUserProvider.getCurrentJwt()).thenReturn(serviceJwt());
        when(currentUserProvider.getServiceClientId()).thenReturn("claims-service");

        assertThatThrownBy(() -> identity.resolveCallingUserId("7"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void serviceMissingXUserId_throwsBadRequest() {
        ReflectionTestUtils.setField(identity, "trustedServiceClientIds", "claims-service");
        when(currentUserProvider.getCurrentJwt()).thenReturn(serviceJwt());
        when(currentUserProvider.getServiceClientId()).thenReturn("claims-service");

        assertThatThrownBy(() -> identity.resolveCallingUserId(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Missing X-User-Id");

        assertThatThrownBy(() -> identity.resolveCallingUserId("  "))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void serviceInvalidXUserId_throwsBadRequest() {
        ReflectionTestUtils.setField(identity, "trustedServiceClientIds", "claims-service");
        when(currentUserProvider.getCurrentJwt()).thenReturn(serviceJwt());
        when(currentUserProvider.getServiceClientId()).thenReturn("claims-service");

        assertThatThrownBy(() -> identity.resolveCallingUserId("not-a-number"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid X-User-Id");
    }
}