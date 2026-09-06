package com.claimassist.platform.policy_service.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.Collections;

@TestConfiguration
public class TestJwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> {
            // Minimal decoder used only in tests to satisfy bean requirement.
            // Tests use SecurityMockMvcRequestPostProcessors.jwt() which sets Authentication directly,
            // so this decoder should not be used in practice, but it must exist.
            try {
                return Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .claims(claims -> claims.putAll(Collections.emptyMap()))
                        .build();
            } catch (Exception e) {
                throw new JwtException("Test JwtDecoder failed");
            }
        };
    }
}
