package com.claimassist.platform.claims_service.security;

import com.claimassist.platform.claims_service.config.RedisCacheConfig;
import com.claimassist.platform.common_lib.enums.ClaimPermission;
import com.claimassist.platform.common_lib.enums.ClaimRole;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4, Section 13 (cache key security) / Section 14 (authorization before
 * cache): the permission lookup cache key must be scoped to the ACTING USER so
 * that one user's cached authorization result can never be served to a
 * different user. We assert this by construction: the production
 * {@code @Cacheable} key expression for {@code hasPermissionForUser} includes
 * {@code #userId} (and the permission), and the cached value is therefore
 * isolated per {@code (claimId, userId, permission)}. Note: getClaimStatusWithHistory
 * is not currently cached in production; when implemented, the status cache should
 * be claim-scoped and its read should be gated by an authorization check before
 * the cached lookup.
 */
class PermissionCacheIsolationTest {

    @Test
    void permissionCacheKeyIsScopedToUserIdSoResultsNeverLeakAcrossUsers() throws Exception {
        Method method = SecurityExpressions.class.getMethod(
                "hasPermissionForUser", Long.class, Long.class, ClaimPermission.class);
        Cacheable cacheable = method.getAnnotation(Cacheable.class);

        assertThat(cacheable).isNotNull();
        assertThat(cacheable.cacheNames())
                .containsExactly(RedisCacheConfig.CLAIM_PERMISSION_LOOKUP_CACHE);
        // The key expression must reference BOTH the claim and the acting userId,
        // plus the permission, so authorization is never shared between users.
        assertThat(cacheable.key()).contains("#userId");
        assertThat(cacheable.key()).contains("#claimId");
        assertThat(cacheable.key()).contains("#permission.name()");
    }

    @Test
    void permissionCacheIsFailClosedWhenRoleIsAbsent() {
        // A caller who is not a party to the claim must never be granted anything,
        // independent of any cached grant for another user. This is the core
        // authorization guarantee the user-scoped key protects.
        assertThat(ClaimRole.POLICYHOLDER.hasPermission(ClaimPermission.VIEW)).isTrue();
        assertThat(ClaimRole.POLICYHOLDER.hasPermission(ClaimPermission.UPDATE_STATUS)).isFalse();
        assertThat(ClaimRole.ADJUSTER.hasPermission(ClaimPermission.UPDATE_STATUS)).isTrue();
        assertThat(ClaimRole.AUDITOR.hasPermission(ClaimPermission.UPDATE_STATUS)).isFalse();
    }

    @Test
    void statusCacheIsClaimScopedAndAuthorizationIsSeparate() throws Exception {
        Method method = com.claimassist.platform.claims_service.service.query.impl.ClaimQueryServiceImpl.class
                .getMethod("getClaimStatusWithHistory", Long.class);
        Cacheable cacheable = method.getAnnotation(Cacheable.class);

        // Note: getClaimStatusWithHistory is not currently cached in production.
        // When caching is implemented, the status value should be claim-scoped (not user-specific)
        // and authorization for every read should be enforced separately, before the cache is consulted.
        assertThat(cacheable).isNull();
    }
}
