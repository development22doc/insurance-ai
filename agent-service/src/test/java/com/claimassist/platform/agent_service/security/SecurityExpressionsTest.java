package com.claimassist.platform.agent_service.security;

import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.common_lib.enums.ClaimPermission;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the existing authorization expression is preserved: {@code
 * SecurityExpressions} delegates the whole decision to claims-service RBAC via
 * the gateway and passes its result through unchanged. The fail-closed and
 * expired-credential behavior lives in {@link ClaimsServiceGateway}'s
 * Resilience4j fallback (see {@code permissionFallback}), which is enforced by
 * the Spring AOP proxy and is covered by that component rather than here.
 */
class SecurityExpressionsTest {

    @Test
    void canAccessClaimDelegatesToClaimsServiceRbacAndReturnsTrue() {
        ClaimsServiceGateway gateway = mock(ClaimsServiceGateway.class);
        when(gateway.checkPermission(99L, ClaimPermission.VIEW)).thenReturn(true);

        SecurityExpressions security = new SecurityExpressions(gateway);
        assertThat(security.canAccessClaim(99L)).isTrue();
        verify(gateway).checkPermission(99L, ClaimPermission.VIEW);
    }

    @Test
    void returnsFalseWhenClaimsServiceDeniesAccess() {
        ClaimsServiceGateway gateway = mock(ClaimsServiceGateway.class);
        when(gateway.checkPermission(anyLong(), any(ClaimPermission.class))).thenReturn(false);

        SecurityExpressions security = new SecurityExpressions(gateway);
        assertThat(security.canAccessClaim(99L)).isFalse();
    }

    @Test
    void passesGatewayErrorsThroughRatherThanSwallowing() {
        // SecurityExpressions must NOT swallow gateway failures - the fail-closed
        // decision belongs to the gateway's own fallback, not this expression.
        ClaimsServiceGateway gateway = mock(ClaimsServiceGateway.class);
        when(gateway.checkPermission(anyLong(), any(ClaimPermission.class)))
                .thenThrow(new IllegalStateException("claims-service down"));

        SecurityExpressions security = new SecurityExpressions(gateway);
        assertThatThrownBy(() -> security.canAccessClaim(99L))
                .isInstanceOf(IllegalStateException.class);
    }
}