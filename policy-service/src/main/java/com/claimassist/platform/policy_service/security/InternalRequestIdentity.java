package com.claimassist.platform.policy_service.security;

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
 * Mirrors the established platform pattern used in Customer service.
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
