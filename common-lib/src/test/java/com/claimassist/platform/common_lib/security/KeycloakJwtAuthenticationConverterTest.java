package com.claimassist.platform.common_lib.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAuthenticationConverterTest {

    private KeycloakJwtAuthenticationConverter converter;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        converter = new KeycloakJwtAuthenticationConverter();
    }

    @Test
    void convert_WithRealmAccessRoles_ShouldMapToRoleAuthorities() {
        // Given
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .claim("realm_access", Map.of("roles", List.of("ADMIN", "USER")))
                .build();

        // When
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Then
        assertThat(token).isInstanceOf(JwtAuthenticationToken.class);
        Collection<? extends GrantedAuthority> authorities = token.getAuthorities();
        assertThat(authorities)
                .hasSize(2)
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"))
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_USER"));
    }

    @Test
    void convert_WithResourceAccessRoles_ShouldMapToRoleAuthorities() {
        // Given
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .claim("resource_access", Map.of(
                        "client1", Map.of("roles", List.of("CLIENT_ADMIN")),
                        "client2", Map.of("roles", List.of("CLIENT_USER"))
                ))
                .build();

        // When
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Then
        assertThat(token).isInstanceOf(JwtAuthenticationToken.class);
        Collection<? extends GrantedAuthority> authorities = token.getAuthorities();
        assertThat(authorities)
                .hasSize(2)
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_CLIENT_ADMIN"))
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_CLIENT_USER"));
    }

    @Test
    void convert_WithBothRealmAndResourceAccessRoles_ShouldMapAllToAuthorities() {
        // Given
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                .claim("resource_access", Map.of(
                        "client1", Map.of("roles", List.of("CLIENT_ADMIN"))
                ))
                .build();

        // When
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Then
        assertThat(token).isInstanceOf(JwtAuthenticationToken.class);
        Collection<? extends GrantedAuthority> authorities = token.getAuthorities();
        assertThat(authorities)
                .hasSize(2)
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"))
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_CLIENT_ADMIN"));
    }

    @Test
    void convert_WithNoRoles_ShouldReturnEmptyAuthorities() {
        // Given
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .build();

        // When
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Then
        assertThat(token).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void convert_WithEmptyRealmAccessRoles_ShouldReturnEmptyAuthorities() {
        // Given
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .claim("realm_access", Map.of("roles", List.of()))
                .build();

        // When
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Then
        assertThat(token).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void convert_WithEmptyResourceAccessRoles_ShouldReturnEmptyAuthorities() {
        // Given
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .claim("resource_access", Map.of(
                        "client1", Map.of("roles", List.of())
                ))
                .build();

        // When
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Then
        assertThat(token).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void convert_WithNullRealmAccess_ShouldReturnEmptyAuthorities() {
        // Given
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .claim("realm_access", null)
                .build();

        // When
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Then
        assertThat(token).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void convert_WithNullResourceAccess_ShouldReturnEmptyAuthorities() {
        // Given
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .claim("resource_access", null)
                .build();

        // When
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Then
        assertThat(token).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void convert_WithInvalidRealmAccessStructure_ShouldHandleGracefully() {
        // Given
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .claim("realm_access", Map.of("roles", "invalid")) // Not a list
                .build();

        // When
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Then
        assertThat(token).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void convert_WithInvalidResourceAccessStructure_ShouldHandleGracefully() {
        // Given
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .claim("resource_access", Map.of(
                        "client1", Map.of("roles", "invalid") // Not a list
                ))
                .build();

        // When
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Then
        assertThat(token).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void convert_ShouldPreserveJwtInToken() {
        // Given
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                .build();

        // When
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Then
        assertThat(token).isInstanceOf(JwtAuthenticationToken.class);
        JwtAuthenticationToken jwtToken = (JwtAuthenticationToken) token;
        assertThat(jwtToken.getToken()).isEqualTo(jwt);
    }
}
