package com.claimassist.platform.policy_service.security;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalRequestIdentityTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

    private InternalRequestIdentity identity;

    @BeforeEach
    void setUp() {
        identity = new InternalRequestIdentity(currentUserProvider);
        ReflectionTestUtils.setField(identity, "trustedServiceClientIds", "trusted-service");
    }

    private static Jwt userJwt(Long userId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-uuid")
                .claim("userId", userId)
                .build();
    }

    private static Jwt serviceJwt(String clientId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("azp", clientId)
                .claim("client_id", clientId)
                .build();
    }

    @Test
    void userTokenReturnsVerifiedUserIdAndIgnoresXUserIdHeader() {
        when(currentUserProvider.getCurrentJwt()).thenReturn(userJwt(10L));
        when(currentUserProvider.getCurrentUserId()).thenReturn(10L);

        Long result = identity.resolveCallingUserId("999");

        assertThat(result).isEqualTo(10L);
    }

    @Test
    void trustedServiceTokenWithXUserIdReturnsParsedId() {
        when(currentUserProvider.getCurrentJwt()).thenReturn(serviceJwt("trusted-service"));
        when(currentUserProvider.getServiceClientId()).thenReturn("trusted-service");

        Long result = identity.resolveCallingUserId("456");

        assertThat(result).isEqualTo(456L);
    }

    @Test
    void untrustedServiceTokenIsRejected() {
        when(currentUserProvider.getCurrentJwt()).thenReturn(serviceJwt("evil"));
        when(currentUserProvider.getServiceClientId()).thenReturn("evil");

        assertThatThrownBy(() -> identity.resolveCallingUserId("456"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Untrusted service caller");
    }

    @Test
    void serviceTokenWithoutXUserIdIsRejected() {
        when(currentUserProvider.getCurrentJwt()).thenReturn(serviceJwt("trusted-service"));
        when(currentUserProvider.getServiceClientId()).thenReturn("trusted-service");

        assertThatThrownBy(() -> identity.resolveCallingUserId(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Missing X-User-Id");
    }
}
