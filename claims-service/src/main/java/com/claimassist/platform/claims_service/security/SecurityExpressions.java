package com.claimassist.platform.claims_service.security;

import com.claimassist.platform.claims_service.repository.ClaimPartyRepository;
import com.claimassist.platform.claims_service.config.RedisCacheConfig;
import com.claimassist.platform.common_lib.enums.ClaimPermission;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Per-claim RBAC - same shape as SecurityExpressions in the Lovable clone
 * (project-level RBAC), evaluated via @PreAuthorize SpEL expressions like
 * "@security.canUpdateStatus(#claimId)". claims-service is the single source
 * of truth for "who can do what to which claim" - agent-service asks it over
 * the network (via ClaimsServiceGateway) rather than duplicating this table.
 */
@Component("security")
@RequiredArgsConstructor
public class SecurityExpressions {

    private final CurrentUserProvider currentUserProvider;
    private final ClaimPartyRepository claimPartyRepository;

    public boolean hasPermission(Long claimId, ClaimPermission permission) {
        Long userId = currentUserProvider.getCurrentUserId();
        return hasPermissionForUser(claimId, userId, permission);
    }

    /**
     * Same check as {@link #hasPermission}, but for an EXPLICITLY supplied actor
     * rather than "whoever is authenticated on this thread". Required by
     * ClaimUpdateConsumer: a Kafka listener thread has no HTTP request, and
     * therefore no populated SecurityContextHolder for CurrentUserProvider to read - the
     * acting user id instead comes from the event payload itself
     * (ClaimUpdateRequestEvent.proposedByUserId, i.e. whichever user was
     * chatting with the AI agent when it proposed the change).
     */
    @Cacheable(
            cacheNames = RedisCacheConfig.CLAIM_PERMISSION_LOOKUP_CACHE,
            key = "#claimId + '-' + #userId + '-' + #permission.name()")
    public boolean hasPermissionForUser(Long claimId, Long userId, ClaimPermission permission) {
        return claimPartyRepository.findRoleByClaimIdAndUserId(claimId, userId)
                .map(role -> role.hasPermission(permission))
                .orElse(false);
    }

    @CacheEvict(cacheNames = RedisCacheConfig.CLAIM_PERMISSION_LOOKUP_CACHE, allEntries = true)
    public void evictPermissionLookupCache() {
        // annotation-driven eviction
    }

    public boolean canView(Long claimId) {
        return hasPermission(claimId, ClaimPermission.VIEW);
    }

    public boolean canUpdateStatus(Long claimId) {
        return hasPermission(claimId, ClaimPermission.UPDATE_STATUS);
    }

    public boolean canSubmitDocuments(Long claimId) {
        return hasPermission(claimId, ClaimPermission.SUBMIT_DOCUMENTS);
    }

    public boolean canManageParties(Long claimId) {
        return hasPermission(claimId, ClaimPermission.MANAGE_PARTIES);
    }
}
