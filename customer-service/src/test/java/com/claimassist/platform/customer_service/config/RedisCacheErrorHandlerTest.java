package com.claimassist.platform.customer_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Phase 3, Section 13: Customer Service must remain functional when Redis is
 * unavailable. The {@link RedisCacheConfig#cacheErrorHandler()} bean is the
 * fail-open seam: any Redis GET/PUT/EVICT/CLEAR failure is logged and swallowed,
 * so the Spring cache interceptor treats a failed GET as a miss (method falls
 * back to the database/source of truth) and a failed PUT/EVICT as a no-op.
 *
 * <p>These tests prove the handler never propagates a Redis failure into the
 * request path - i.e. a cache outage can never take down a read or a write.
 */
class RedisCacheErrorHandlerTest {

    private final CacheErrorHandler handler = new RedisCacheConfig().cacheErrorHandler();
    private final Cache cache = mock(Cache.class);

    @Test
    void cacheGetErrorIsSwallowedSoReadFallsBackToSourceOfTruth() {
        assertThatCode(() -> handler.handleCacheGetError(new RuntimeException("redis down"), cache, "key"))
                .doesNotThrowAnyException();
    }

    @Test
    void cachePutErrorIsSwallowedSoDbResultStillReturned() {
        assertThatCode(() -> handler.handleCachePutError(new RuntimeException("redis down"), cache, "key", "value"))
                .doesNotThrowAnyException();
    }

    @Test
    void cacheEvictErrorIsSwallowedSoWriteStillCompletes() {
        assertThatCode(() -> handler.handleCacheEvictError(new RuntimeException("redis down"), cache, "key"))
                .doesNotThrowAnyException();
    }

    @Test
    void cacheClearErrorIsSwallowed() {
        assertThatCode(() -> handler.handleCacheClearError(new RuntimeException("redis down"), cache))
                .doesNotThrowAnyException();
    }
}