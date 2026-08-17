package com.claimassist.platform.agent_service.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the SecurityFallbackConfig noop JwtDecoder gating.
 * <p>
 * The noop decoder must ONLY be created when the real OAuth2 resource-server
 * {@code issuer-uri} is absent (pure local development without Keycloak). It must
 * NEVER preempt Spring Boot's real JWKS-based decoder when an issuer URI is
 * configured; otherwise the agent accepts any unsigned/forged token and loses the
 * {@code userId} claim, which breaks user identification (classify).
 */
class SecurityFallbackConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(SecurityFallbackConfig.class);

    @Test
    void noopDecoderPresentOnlyWhenIssuerUriAbsent() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(JwtDecoder.class);
            Jwt jwt = ctx.getBean(JwtDecoder.class).decode("garbage.token");
            assertThat(jwt.getSubject()).isEqualTo("local");
        });
    }

    @Test
    void noopDecoderAbsentWhenIssuerUriConfigured() {
        runner
                .withPropertyValues(
                        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/claimassist")
                .run(ctx -> {
                    // The insecure noop decoder must not be registered when a real issuer URI is configured.
                    assertThat(ctx).doesNotHaveBean(JwtDecoder.class);
                });
    }
}
