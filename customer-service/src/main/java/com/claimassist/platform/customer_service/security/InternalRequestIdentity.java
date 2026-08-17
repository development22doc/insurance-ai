package com.claimassist.platform.customer_service.security;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.security.CallerType;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the effective acting user id for internal (/internal/v1/**) requests.
 *
 * <p>F-2 (Phase 2) completion of the USER vs SERVICE token distinction at the
 * customer internal endpoint:
 *
 * <ul>
 *   <li><b>USER token</b> - the caller's own authenticated {@code userId} is used
 *       and an {@code X-User-Id} header is deliberately NOT trusted (a verified
 *       JWT identity always wins; a client-supplied id must never override it).
 *       Fail-closed: a user token without a {@code userId} claim is rejected.</li>
 *   <li><b>SERVICE token</b> - the calling service must be in the configured
 *       trusted allowlist ({@code security.internal.trusted-service-client-ids}),
 *       and it must supply an explicit {@code X-User-Id} for the target user it is
 *       acting on behalf of. Untrusted services and missing/invalid X-User-Id are
 *       rejected. The service token identifies WHO is calling; X-User-Id identifies
 *       FOR WHOM; the endpoint/service still performs resource-ownership checks.</li>
 * </ul>
 *
 * <p>Never manufactures a userId from a service token, never makes the endpoint
 * public, and never bypasses the per-resource ownership check that follows.
 */
@Component
@RequiredArgsConstructor
public class InternalRequestIdentity {

    private final CurrentUserProvider currentUserProvider;

    @Value("${security.internal.trusted-service-client-ids:}")
    private String trustedServiceClientIds;

    public Long resolveCallingUserId(String xUserIdHeader) {
        // Fail-closed: no authenticated JWT at all -> AuthenticationCredentialsNotFoundException.
        var jwt = currentUserProvider.getCurrentJwt();

        if (CurrentUserProvider.classify(jwt) == CallerType.USER) {
            // User token: use the verified JWT identity. Ignore X-User-Id.
            return currentUserProvider.getCurrentUserId();
        }

        // SERVICE token path.
        String serviceClientId = currentUserProvider.getServiceClientId();
        if (serviceClientId == null || !trustedClients().contains(serviceClientId)) {
            throw new AccessDeniedException(
                    "Untrusted service caller for internal endpoint: " + serviceClientId);
        }

        if (xUserIdHeader == null || xUserIdHeader.isBlank()) {
            throw new BadRequestException("Missing X-User-Id for service caller");
        }

        try {
            return Long.valueOf(xUserIdHeader.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid X-User-Id");
        }
    }

    private Set<String> trustedClients() {
        if (trustedServiceClientIds == null || trustedServiceClientIds.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(trustedServiceClientIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}