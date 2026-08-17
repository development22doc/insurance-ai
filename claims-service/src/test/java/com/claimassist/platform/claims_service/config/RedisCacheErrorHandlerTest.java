package com.claimassist.platform.claims_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Phase 4, Section 16 (Redis failure) / Section 14 (authorization before cache):
 * the cache is a pure optimization and PostgreSQL is the source of truth. The
 * shared {@link CacheErrorHandler} is fail-open for the cache PLUMBING, which
 * means a Redis GET failure is treated as a cache MISS and the underlying method
 * re-runs against the database. For the permission lookup cache this is exactly
 * the fail-CLOSED authorization behaviour we need: when the cache cannot be
 * read, authorization is re-evaluated from the DB, so a Redis outage can never
 * turn "unable to check permission" into an ALLOW.
 */
class RedisCacheErrorHandlerTest {

    private final CacheErrorHandler handler = new RedisCacheConfig().cacheErrorHandler();
    private final Cache cache = mock(Cache.class);

    @Test
    void cacheGetErrorIsSwallowedSoAuthorizationFallsBackToDatabase() {
        assertThatCode(() -> handler.handleCacheGetError(new RuntimeException("redis down"), cache, "key"))
                .doesNotThrowAnyException();
    }

    @Test
    void cachePutErrorIsSwallowedSoDatabaseResultStillReturned() {
        assertThatCode(() -> handler.handleCachePutError(new RuntimeException("redis down"), cache, "key", "value"))
                .doesNotThrowAnyException();
    }

    @Test
    void cacheEvictErrorIsSwallowedSoStatusWriteStillCommits() {
        assertThatCode(() -> handler.handleCacheEvictError(new RuntimeException("redis down"), cache, "key"))
                .doesNotThrowAnyException();
    }

    @Test
    void cacheClearErrorIsSwallowed() {
        assertThatCode(() -> handler.handleCacheClearError(new RuntimeException("redis down"), cache))
                .doesNotThrowAnyException();
    }
}
