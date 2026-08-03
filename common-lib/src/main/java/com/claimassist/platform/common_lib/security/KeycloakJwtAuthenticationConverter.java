package com.claimassist.platform.common_lib.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps Keycloak realm roles (realm_access.roles) and client roles
 * (resource_access.{client}.roles) onto Spring Security GrantedAuthorities,
 * ROLE_-prefixed so they work with both {@code hasRole(...)} in a
 * SecurityFilterChain and {@code @PreAuthorize("hasRole('...')")}.
 * <p>
 * Shared by every servlet-based resource server (customer/claims/agent
 * service, used directly as a JwtAuthenticationConverter) AND by the
 * reactive api-gateway (wrapped in a
 * {@code ReactiveJwtAuthenticationConverterAdapter}), so the role-mapping
 * rule lives in exactly one place.
 * <p>
 * Important scope note: this maps *global* Keycloak roles only (e.g. an
 * "ADMIN" or "SUPPORT" style role, if/when one is introduced). The
 * platform's real, fine-grained authorization model - who can do what on one
 * specific claim - is the separate, database-backed ClaimRole/ClaimPermission
 * system in claims-service (see SecurityExpressions), which is deliberately
 * untouched by this migration; it is keyed off
 * {@link CurrentUserProvider#getCurrentUserId()}, not off these authorities.
 */
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, extractAuthorities(jwt));
    }

    @SuppressWarnings("unchecked")
    private Set<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        }

        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null) {
            resourceAccess.values().forEach(client -> {
                if (client instanceof Map<?, ?> clientMap && clientMap.get("roles") instanceof List<?> roles) {
                    roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
                }
            });
        }

        return authorities;
    }
}
