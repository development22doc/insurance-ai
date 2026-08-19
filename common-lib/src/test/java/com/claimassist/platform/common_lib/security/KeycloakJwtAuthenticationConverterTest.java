package com.claimassist.platform.common_lib.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    @Test
    void mapsRealmRolesToPrefixedAuthorities() {
        Jwt jwt = Jwt.withTokenValue("tok").header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of("ADMIN", "SUPPORT")))
                .build();
        AbstractAuthenticationToken token = converter.convert(jwt);
        assertThat(authorities(token)).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_SUPPORT");
    }

    @Test
    void mapsClientRolesToPrefixedAuthorities() {
        Jwt jwt = Jwt.withTokenValue("tok").header("alg", "none")
                .claim("resource_access", Map.of(
                        "claimassist-customer-app", Map.of("roles", List.of("customer_read"))))
                .build();
        AbstractAuthenticationToken token = converter.convert(jwt);
        assertThat(authorities(token)).containsExactlyInAnyOrder("ROLE_customer_read");
    }

    @Test
    void combinesRealmAndClientRoles() {
        Jwt jwt = Jwt.withTokenValue("tok").header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                .claim("resource_access", Map.of(
                        "app", Map.of("roles", List.of("read", "write"))))
                .build();
        AbstractAuthenticationToken token = converter.convert(jwt);
        assertThat(authorities(token)).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_read", "ROLE_write");
    }

    @Test
    void noRolesYieldsEmptyAuthorities() {
        Jwt jwt = Jwt.withTokenValue("tok").header("alg", "none")
                .claim("sub", "uuid").build();
        AbstractAuthenticationToken token = converter.convert(jwt);
        assertThat(authorities(token)).isEmpty();
    }

    @Test
    void producesJwtAuthenticationTokenCarryingOriginalJwt() {
        Jwt jwt = Jwt.withTokenValue("tok").header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                .build();
        AbstractAuthenticationToken token = converter.convert(jwt);
        assertThat(token).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(((JwtAuthenticationToken) token).getToken()).isSameAs(jwt);
    }

    private Set<String> authorities(AbstractAuthenticationToken token) {
        Collection<? extends GrantedAuthority> granted = token.getAuthorities();
        return granted.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}