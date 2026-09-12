package com.claimassist.platform.policy_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import static org.mockito.Mockito.*;

/**
 * Unit test for the fail-open cache error handler.
 *
 * <p>Verifies that cache errors are logged but do not propagate,
 * ensuring the application continues to function with the database
 * as the source of truth when Redis is unavailable.
 */
class RedisCacheErrorHandlerTest {

    @Test
    void handleCacheGetError_logsAndSwallowsException() {
        CacheErrorHandler handler = new RedisCacheConfig().cacheErrorHandler();
        Cache mockCache = mock(Cache.class);
        RuntimeException testException = new RuntimeException("Redis connection failed");

        // Should not throw exception
        handler.handleCacheGetError(testException, mockCache, "testKey");

        // Verify cache name and key were used in logging (via log.warn)
        verify(mockCache, atLeastOnce()).getName();
    }

    @Test
    void handleCachePutError_logsAndSwallowsException() {
        CacheErrorHandler handler = new RedisCacheConfig().cacheErrorHandler();
        Cache mockCache = mock(Cache.class);
        RuntimeException testException = new RuntimeException("Redis write failed");

        // Should not throw exception
        handler.handleCachePutError(testException, mockCache, "testKey", "testValue");

        verify(mockCache, atLeastOnce()).getName();
    }

    @Test
    void handleCacheEvictError_logsAndSwallowsException() {
        CacheErrorHandler handler = new RedisCacheConfig().cacheErrorHandler();
        Cache mockCache = mock(Cache.class);
        RuntimeException testException = new RuntimeException("Redis evict failed");

        // Should not throw exception
        handler.handleCacheEvictError(testException, mockCache, "testKey");

        verify(mockCache, atLeastOnce()).getName();
    }

    @Test
    void handleCacheClearError_logsAndSwallowsException() {
        CacheErrorHandler handler = new RedisCacheConfig().cacheErrorHandler();
        Cache mockCache = mock(Cache.class);
        RuntimeException testException = new RuntimeException("Redis clear failed");

        // Should not throw exception
        handler.handleCacheClearError(testException, mockCache);

        verify(mockCache, atLeastOnce()).getName();
    }
}
