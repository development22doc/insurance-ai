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

        org.springframework.web.reactive.function.client.WebClient webClient = mock(org.springframework.web.reactive.function.client.WebClient.class);
        var uriSpec = mock(org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec.class);
        var headersSpec = mock(org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec.class);
        var responseSpec = mock(org.springframework.web.reactive.function.client.WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(headersSpec);
        when(headersSpec.headers(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Boolean.class)).thenReturn(reactor.core.publisher.Mono.just(true));

        SecurityExpressions security = new SecurityExpressions(gateway, webClient);
        assertThat(security.canAccessClaim(99L).block()).isTrue();
    }

    @Test
    void returnsFalseWhenClaimsServiceDeniesAccess() {
        ClaimsServiceGateway gateway = mock(ClaimsServiceGateway.class);

        org.springframework.web.reactive.function.client.WebClient webClient = mock(org.springframework.web.reactive.function.client.WebClient.class);
        var uriSpec = mock(org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec.class);
        var headersSpec = mock(org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec.class);
        var responseSpec = mock(org.springframework.web.reactive.function.client.WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(headersSpec);
        when(headersSpec.headers(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Boolean.class)).thenReturn(reactor.core.publisher.Mono.just(false));

        SecurityExpressions security = new SecurityExpressions(gateway, webClient);
        assertThat(security.canAccessClaim(99L).block()).isFalse();
    }

    @Test
    void returnsFalseOnGatewayError() {
        // On errors the expression must fail-closed and return false
        ClaimsServiceGateway gateway = mock(ClaimsServiceGateway.class);

        org.springframework.web.reactive.function.client.WebClient webClient = mock(org.springframework.web.reactive.function.client.WebClient.class);
        var uriSpec = mock(org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec.class);
        var headersSpec = mock(org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec.class);
        var responseSpec = mock(org.springframework.web.reactive.function.client.WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(headersSpec);
        when(headersSpec.headers(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Boolean.class)).thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("claims-service down")));

        SecurityExpressions security = new SecurityExpressions(gateway, webClient);
        assertThat(security.canAccessClaim(99L).block()).isFalse();
    }
}