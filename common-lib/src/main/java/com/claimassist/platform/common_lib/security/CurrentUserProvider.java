package com.claimassist.platform.common_lib.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads "who is the current user" off the Keycloak-issued JWT that Spring
 * Security's OAuth2 Resource Server support places on the SecurityContext.
 * This is the direct replacement for the old AuthUtil.getCurrentUser() /
 * getCurrentUserId() (custom-JWT era) - every call site that used to depend
 * on AuthUtil for that purpose now depends on this class instead. The
 * getCurrentUserId() signature is unchanged on purpose, so the migration is
 * a mechanical rename at almost every call site.
 * <p>
 * The internal, numeric, database-owned user id (the value every foreign key
 * in this platform is keyed on - ClaimParty.userId, Customer.id, etc.) is
 * carried in a custom "userId" claim. That claim is populated by a Keycloak
 * protocol mapper (see realm-export.json) sourced from a "legacy_user_id"
 * user attribute set at provisioning time. Keycloak's own "sub" claim is a
 * UUID and is deliberately NOT used as the business user id anywhere in this
 * codebase, to avoid a database-wide key-type migration.
 * <p>
 * NOTE: like the old AuthUtil, this only works on a thread with a populated
 * SecurityContextHolder (i.e. an HTTP request thread). Code running on a
 * message-listener thread (e.g. claims-service's ClaimUpdateConsumer) has no
 * SecurityContext to read and must keep receiving the acting user id
 * explicitly in the event payload instead - that pattern is unrelated to the
 * auth mechanism and is unaffected by this migration.
 */
public class CurrentUserProvider {

    public Long getCurrentUserId() {
        return extractUserId(getCurrentJwt());
    }

    public String getCurrentUsername() {
        return getCurrentJwt().getClaimAsString("preferred_username");
    }

    public String getCurrentName() {
        String name = getCurrentJwt().getClaimAsString("name");
        return name != null ? name : getCurrentUsername();
    }

    public Jwt getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated Keycloak JWT found");
        }
        return jwt;
    }

    private Long extractUserId(Jwt jwt) {
        Object userId = jwt.getClaim("userId");
        if (userId == null) {
            throw new AuthenticationCredentialsNotFoundException(
                    "JWT is missing the required 'userId' claim - check the Keycloak protocol mapper " +
                            "('userId' <- 'legacy_user_id' user attribute) in realm-export.json");
        }
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(userId.toString());
    }
}
