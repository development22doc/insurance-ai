package com.claimassist.platform.agent_service.cache;

import com.claimassist.platform.agent_service.config.CacheProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance-oriented test: caching must actually REDUCE repeated backend
 * calls. We assert the measured backend-call count (not a latency figure),
 * because only call-count is deterministic here.
 */
class CachePerformanceTest {

    private record Data(Long id, String value) {}

    private static final TypeReference<Data> TYPE = new TypeReference<Data>() {};

    static final class MemBackend implements CacheBackend {
        final Map<String, String> store = new ConcurrentHashMap<>();
        @Override public String get(String k) { return store.get(k); }
        @Override public void set(String k, String v, Duration t) { store.put(k, v); }
        @Override public void delete(String k) { store.remove(k); }
    }

    @Test
    void cachingReducesBackendCallsAcrossRepeatedReads() {
        AtomicInteger backendCalls = new AtomicInteger();

        // WITHOUT-cache baseline: a fresh service with caching disabled → both
        // requests hit the backend.
        CacheProperties off = new CacheProperties();
        off.setEnabled(false);
        CacheService noCache = new CacheService(new MemBackend(), new ObjectMapper(), off, new CacheMetrics());
        for (int i = 0; i < 2; i++) {
            noCache.getOrLoad("k", TYPE, Duration.ofSeconds(1),
                    () -> { backendCalls.incrementAndGet(); return new Data(1L, "x"); }, v -> true);
        }
        assertThat(backendCalls.get()).isEqualTo(2); // no cache → 2 backend calls

        // WITH-cache: two requests → one backend call, second is a hit.
        backendCalls.set(0);
        CacheService svc = new CacheService(new MemBackend(), new ObjectMapper(),
                new CacheProperties(), new CacheMetrics());
        svc.getOrLoad("k", TYPE, Duration.ofSeconds(60),
                () -> { backendCalls.incrementAndGet(); return new Data(9L, "cached"); }, v -> true);
        svc.getOrLoad("k", TYPE, Duration.ofSeconds(60),
                () -> { backendCalls.incrementAndGet(); return new Data(9L, "cached"); }, v -> true);

        assertThat(backendCalls.get()).isEqualTo(1); // second read served from cache
        assertThat(svc.metrics().hits()).isEqualTo(1);
        assertThat(svc.metrics().misses()).isEqualTo(1);
    }

    @Test
    void invalidationForcesFreshBackendRead() {
        CacheService svc = new CacheService(new MemBackend(), new ObjectMapper(),
                new CacheProperties(), new CacheMetrics());
        AtomicInteger backendCalls = new AtomicInteger();

        svc.getOrLoad("k", TYPE, Duration.ofSeconds(60),
                () -> { backendCalls.incrementAndGet(); return new Data(1L, "OLD"); }, v -> true);
        svc.evict("k"); // write accepted → invalidate
        svc.getOrLoad("k", TYPE, Duration.ofSeconds(60),
                () -> { backendCalls.incrementAndGet(); return new Data(2L, "FRESH"); }, v -> true);

        assertThat(backendCalls.get()).isEqualTo(2); // fresh backend data after invalidation
    }
}