package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.config.KeycloakProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * Phase 2 PKCE state-management tests. Verifies the state is persisted to Redis
 * with a TTL and that one-time consumption (replay protection) is delegated to
 * the atomic Redis GETDEL.
 */
class OAuth2AuthorizationServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private OAuth2AuthorizationService service;

    @BeforeEach
    void setUp() {
        PkceService pkceService = new PkceService();
        KeycloakProperties props = new KeycloakProperties(
                "http://localhost:8180", "claimassist", "customer-app",
                "secret", "http://localhost:8080/customer/auth/callback",
                "admin", "admin-secret");
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        service = new OAuth2AuthorizationService(pkceService, props, redis);
        setField(service, "stateTtlSeconds", 600L);
    }

    @Test
    void createAuthorizationRequestStoresStateWithTtlAndBuildsUrl() {
        OAuth2AuthorizationService.AuthorizationRequest req =
                service.createAuthorizationRequest();

        verify(valueOps).set(eq("pkce:state:" + req.state()), anyString(), eq(Duration.ofSeconds(600)));

        assertThat(req.authorizationUrl()).contains("client_id=customer-app");
        assertThat(req.authorizationUrl()).contains("response_type=code");
        assertThat(req.authorizationUrl()).contains("code_challenge_method=S256");
        assertThat(req.authorizationUrl()).contains("code_challenge=");
        assertThat(req.authorizationUrl()).contains("state=" + req.state());
        assertThat(req.authorizationUrl()).contains("redirect_uri=");
    }

    @Test
    void consumeCodeVerifierUsesAtomicGetDelete() {
        when(valueOps.getAndDelete("pkce:state:st1")).thenReturn("the-verifier");

        String verifier = service.consumeCodeVerifier("st1");

        assertThat(verifier).isEqualTo("the-verifier");
        verify(valueOps).getAndDelete("pkce:state:st1");
    }

    @Test
    void replayedStateReturnsNull() {
        // First consume returns the verifier; the GETDEL is atomic so a replay
        // returns null - no second value can be returned for a consumed state.
        when(valueOps.getAndDelete("pkce:state:st2")).thenReturn(null);

        assertThat(service.consumeCodeVerifier("st2")).isNull();
    }

    @Test
    void blankOrNullStateReturnsNullWithoutCallingRedis() {
        assertThat(service.consumeCodeVerifier(null)).isNull();
        assertThat(service.consumeCodeVerifier("")).isNull();
        assertThat(service.consumeCodeVerifier("   ")).isNull();
    }
}