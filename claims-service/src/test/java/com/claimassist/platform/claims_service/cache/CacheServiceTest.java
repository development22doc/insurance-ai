package com.claimassist.platform.claims_service.cache;

import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheServiceTest {

    private final ObjectProvider<RedisTemplate<String, Object>> provider = mock(ObjectProvider.class);
    private final RedisTemplate<String, Object> template = mock(RedisTemplate.class);
    private final PerformanceLogger performanceLogger = mock(PerformanceLogger.class);

    private CacheService service() {
        return new CacheService(provider, performanceLogger);
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, Object> valueOps() {
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        return ops;
    }

    @Test
    void getReturnsCachedValueOnHit() {
        when(provider.getIfAvailable()).thenReturn(template);
        ValueOperations<String, Object> ops = valueOps();
        when(ops.get("k")).thenReturn("stored");

        String result = service().get("k", String.class);

        assertThat(result).isEqualTo("stored");
        verify(performanceLogger).log(eq("CACHE"), anyString(), anyLong(), any());
    }

    @Test
    void getReturnsNullWhenTemplateUnavailable() {
        when(provider.getIfAvailable()).thenReturn(null);
        assertThat(service().get("k", String.class)).isNull();
    }

    @Test
    void getReturnsNullAndDeletesOnTypeMismatch() {
        when(provider.getIfAvailable()).thenReturn(template);
        ValueOperations<String, Object> ops = valueOps();
        when(ops.get("k")).thenReturn(123L);

        Long result = service().get("k", Long.class);

        assertThat(result).isNull();
        verify(template).delete("k");
    }

    @Test
    void getReturnsNullOnException() {
        when(provider.getIfAvailable()).thenReturn(template);
        ValueOperations<String, Object> ops = valueOps();
        when(ops.get("k")).thenThrow(new RuntimeException("redis down"));

        assertThat(service().get("k", String.class)).isNull();
    }

    @Test
    void setStoresValueWithTtl() {
        when(provider.getIfAvailable()).thenReturn(template);
        ValueOperations<String, Object> ops = valueOps();

        service().set("k", "v", 300);

        verify(ops).set(eq("k"), eq("v"), eq(300L), any(java.util.concurrent.TimeUnit.class));
    }

    @Test
    void setSkipsNullValue() {
        when(provider.getIfAvailable()).thenReturn(template);
        service().set("k", null, 300);
        verify(template, never()).opsForValue();
    }

    @Test
    void setSkipsWhenTemplateUnavailable() {
        when(provider.getIfAvailable()).thenReturn(null);
        service().set("k", "v", 300);
        verify(template, never()).opsForValue();
    }

    @Test
    void deleteRemovesKey() {
        when(provider.getIfAvailable()).thenReturn(template);
        service().delete("k");
        verify(template).delete("k");
    }

    @Test
    void deleteByPatternDeletesMatchedKeys() {
        when(provider.getIfAvailable()).thenReturn(template);
        when(template.keys("claim:1:*")).thenReturn(Set.of("a", "b"));

        service().deleteByPattern("claim:1:*");

        verify(template).delete(Set.of("a", "b"));
    }

    @Test
    void deleteByPatternDoesNothingWhenNoKeys() {
        when(provider.getIfAvailable()).thenReturn(template);
        when(template.keys("claim:9:*")).thenReturn(Set.of());

        service().deleteByPattern("claim:9:*");

        verify(template, never()).delete(any(java.util.Collection.class));
    }

    @Test
    void countKeysByPatternReturnsSize() {
        when(provider.getIfAvailable()).thenReturn(template);
        when(template.keys("lookup:*")).thenReturn(Set.of("x", "y", "z"));

        assertThat(service().countKeysByPattern("lookup:*")).isEqualTo(3);
    }

    @Test
    void countKeysByPatternReturnsMinusOneOnException() {
        when(provider.getIfAvailable()).thenReturn(template);
        when(template.keys("lookup:*")).thenThrow(new RuntimeException("down"));

        assertThat(service().countKeysByPattern("lookup:*")).isEqualTo(-1);
    }

    @Test
    void clearAllFlushesRedis() {
        when(provider.getIfAvailable()).thenReturn(template);
        service().clearAll();
        verify(template).execute(any(RedisCallback.class));
    }

    @Test
    void invalidateMethodsDelegateToPatternEviction() {
        when(provider.getIfAvailable()).thenReturn(template);
        when(template.keys(anyString())).thenReturn(Set.of("k"));

        CacheService svc = service();
        svc.invalidateCustomerCache(5L);
        svc.invalidateCustomerPoliciesCache(6L);
        svc.invalidateClaimCache(7L);
        svc.invalidateLookupCache();

        verify(template).keys("customer:5:*");
        verify(template).keys("policy:customer:6:*");
        verify(template).keys("claim:7:*");
        verify(template).keys("lookup:*");
    }

    @Test
    void keyGenerationStaticHelpers() {
        assertThat(CacheService.customerKey(1L)).isEqualTo("customer:1");
        assertThat(CacheService.customerPoliciesKey(2L)).isEqualTo("policy:customer:2");
        assertThat(CacheService.policyKey(3L)).isEqualTo("policy:3");
        assertThat(CacheService.claimStatusKey(4L)).isEqualTo("claim:4:status");
        assertThat(CacheService.claimKey(5L)).isEqualTo("claim:5");
        assertThat(CacheService.lookupKey("incident-types")).isEqualTo("lookup:incident-types");
        assertThat(CacheService.CUSTOMER_CACHE_TTL).isEqualTo(300);
        assertThat(CacheService.POLICY_CACHE_TTL).isEqualTo(600);
    }
}