package com.claimassist.platform.policy_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

public class RedisCacheConfigTest {

    @Test
    public void policyContractKeys_includeCustomerId_andPolicyId() {
        String listKey = com.claimassist.platform.policy_service.service.PolicyContractReadService.policyContractListKey(42L);
        String detailKey = com.claimassist.platform.policy_service.service.PolicyContractReadService.policyContractDetailKey(42L, 99L);

        assertEquals("contract:customer:42:policies", listKey);
        assertEquals("contract:customer:42:policy:99", detailKey);
    }

    @Test
    public void cacheErrorHandler_doesNotThrow_onErrors() {
        RedisCacheConfig cfg = new RedisCacheConfig();
        CacheErrorHandler handler = cfg.cacheErrorHandler();
        Cache mockCache = mock(Cache.class);

        assertDoesNotThrow(() -> handler.handleCacheGetError(new RuntimeException("boom"), mockCache, "k"));
        assertDoesNotThrow(() -> handler.handleCachePutError(new RuntimeException("boom"), mockCache, "k", "v"));
        assertDoesNotThrow(() -> handler.handleCacheEvictError(new RuntimeException("boom"), mockCache, "k"));
        assertDoesNotThrow(() -> handler.handleCacheClearError(new RuntimeException("boom"), mockCache));
    }
}
