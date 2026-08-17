package com.claimassist.platform.common_lib.security;

import com.claimassist.platform.common_lib.observability.LoggingConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SharedSecurityRequestInterceptorTest {

    private final SharedSecurityAutoConfiguration autoConfig = new SharedSecurityAutoConfiguration();
    private RequestTemplate template;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        MDC.clear();
        template = new RequestTemplate();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private Jwt userJwt(Long userId) {
        return Jwt.withTokenValue("end.user.token." + userId)
                .header("alg", "none")
                .claim("userId", userId)
                .claim("azp", "claimassist-customer-app")
                .build();
    }

    private Jwt serviceJwt(String clientId) {
        return Jwt.withTokenValue("service.token." + clientId)
                .header("alg", "none")
                .claim("azp", clientId)
                .claim("client_id", clientId)
                .build();
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ServiceClientCredentialsTokenProvider> providerOf(ServiceClientCredentialsTokenProvider tp) {
        ObjectProvider<ServiceClientCredentialsTokenProvider> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(tp);
        return provider;
    }

    private void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private String header(String name) {
        Map<String, Collection<String>> headers = template.headers();
        Collection<String> values = headers.get(name);
        return values == null ? null : values.iterator().next();
    }

    @Test
    void propagatesEndUserJwtWhenUserAuthenticated() {
        authenticate(userJwt(1L));
        RequestInterceptor interceptor = autoConfig.requestInterceptor(providerOf(null));
        interceptor.apply(template);
        assertThat(header("Authorization")).isEqualTo("Bearer end.user.token.1");
    }

    @Test
    void fallsBackToServiceTokenWhenNoAuthentication() {
        ServiceClientCredentialsTokenProvider tp = mock(ServiceClientCredentialsTokenProvider.class);
        when(tp.getAccessToken()).thenReturn("service.access.token");
        RequestInterceptor interceptor = autoConfig.requestInterceptor(providerOf(tp));
        interceptor.apply(template);
        assertThat(header("Authorization")).isEqualTo("Bearer service.access.token");
    }

    @Test
    void noAuthorizationHeaderWhenNoAuthAndNoServiceTokenProvider() {
        RequestInterceptor interceptor = autoConfig.requestInterceptor(providerOf(null));
        interceptor.apply(template);
        assertThat(header("Authorization")).isNull();
    }

    @Test
    void propagatesServiceJwtWhenServiceAuthenticated() {
        authenticate(serviceJwt("claimassist-admin-service"));
        RequestInterceptor interceptor = autoConfig.requestInterceptor(providerOf(null));
        interceptor.apply(template);
        assertThat(header("Authorization")).isEqualTo("Bearer service.token.claimassist-admin-service");
    }

    @Test
    void propagatesCorrelationIdFromMdc() {
        MDC.put(LoggingConstants.MDC_CORRELATION_ID, "corr-123");
        RequestInterceptor interceptor = autoConfig.requestInterceptor(providerOf(null));
        interceptor.apply(template);
        assertThat(header(LoggingConstants.CORRELATION_ID_HEADER)).isEqualTo("corr-123");
    }

    @Test
    void ignoresServiceTokenProviderFailureAndKeepsRequestUntouched() {
        ServiceClientCredentialsTokenProvider tp = mock(ServiceClientCredentialsTokenProvider.class);
        when(tp.getAccessToken()).thenThrow(new IllegalStateException("no registration"));
        RequestInterceptor interceptor = autoConfig.requestInterceptor(providerOf(tp));
        interceptor.apply(template);
        assertThat(header("Authorization")).isNull();
    }
}