package com.claimassist.platform.common_lib.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads "who is the current caller" off the Keycloak-issued JWT that Spring
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
 * <p>
 * <b>F-2 (Phase 1 shared-foundation fix):</b> the platform now classifies the
 * current bearer token as either a {@link CallerType#USER} token or a
 * {@link CallerType#SERVICE} token. A service-to-service client-credentials
 * token is a trusted service and MUST NOT be required to carry an end-user
 * {@code userId} claim. {@link #getCurrentUserId()} therefore stays fail-closed
 * for genuinely user-required paths, but the non-throwing {@link #callerType()},
 * {@link #isServiceToken()} and {@link #getServiceClientId()} methods let shared
 * infrastructure and internal endpoints distinguish a service caller explicitly
 * instead of assuming every token has a userId.
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

    /**
     * Classifies the current caller as a user or a trusted service. Never
     * throws on a well-formed Keycloak token; only throws if there is no
     * authenticated JWT on the thread at all.
     */
    public CallerType callerType() {
        return classify(getCurrentJwt());
    }

    /** True when the current token is a machine-to-machine client-credentials token. */
    public boolean isServiceToken() {
        return callerType() == CallerType.SERVICE;
    }

    /** True when the current token is an end-user token. */
    public boolean isUserToken() {
        return callerType() == CallerType.USER;
    }

    /**
     * Returns the OAuth2 client id that a {@link CallerType#SERVICE} token was
     * issued to (from {@code azp}, falling back to {@code client_id}), or
     * {@code null} for a user token / when no service client id is present.
     */
    public String getServiceClientId() {
        Jwt jwt = getCurrentJwt();
        if (classify(jwt) != CallerType.SERVICE) {
            return null;
        }
        String azp = jwt.getClaimAsString("azp");
        if (azp != null && !azp.isBlank()) {
            return azp;
        }
        return jwt.getClaimAsString("client_id");
    }

    /**
     * Classifies a Keycloak JWT without touching the SecurityContext, so shared
     * infrastructure (Feign interceptors, gateways) and tests can classify
     * tokens explicitly.
     */
    public static CallerType classify(Jwt jwt) {
        if (jwt.getClaim("userId") != null) {
            return CallerType.USER;
        }
        String azp = jwt.getClaimAsString("azp");
        String clientId = jwt.getClaimAsString("client_id");
        if ((azp != null && !azp.isBlank()) || (clientId != null && !clientId.isBlank())) {
            return CallerType.SERVICE;
        }
        throw new AuthenticationCredentialsNotFoundException(
                "Cannot classify JWT: neither an end-user 'userId' claim nor a service 'azp'/'client_id' claim is present");
    }

    private Long extractUserId(Jwt jwt) {
        if (classify(jwt) == CallerType.SERVICE) {
            throw new AuthenticationCredentialsNotFoundException(
                    "The current token is a service (client-credentials) token and does not carry an end-user " +
                            "'userId' claim. A service caller must act on behalf of a specific user only via a " +
                            "propagated end-user token or an explicit user-id parameter, never via a userId claim.");
        }
        // classify() only returns USER when the 'userId' claim is present, so it is
        // guaranteed non-null here.
        Object userId = jwt.getClaim("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(userId.toString());
    }
}