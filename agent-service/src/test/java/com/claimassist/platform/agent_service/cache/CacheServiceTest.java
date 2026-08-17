package com.claimassist.platform.agent_service.cache;

import com.claimassist.platform.agent_service.config.CacheProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CacheProperties props = new CacheProperties();

    private static final TypeReference<ClaimStub> TYPE = new TypeReference<ClaimStub>() {};
    private static final TypeReference<List<ClaimStub>> LIST_TYPE = new TypeReference<List<ClaimStub>>() {};

    private record ClaimStub(Long claimId, String status) {}

    private static final Duration TTL = Duration.ofSeconds(30);

    /** In-memory {@link CacheBackend} for deterministic unit tests. */
    static final class FakeBackend implements CacheBackend {
        final Map<String, String> store = new ConcurrentHashMap<>();
        final Map<String, Duration> ttls = new ConcurrentHashMap<>();
        final AtomicInteger gets = new AtomicInteger();
        final AtomicInteger sets = new AtomicInteger();
        final AtomicInteger deletes = new AtomicInteger();

        @Override public String get(String key) { gets.incrementAndGet(); return store.get(key); }
        @Override public void set(String key, String json, Duration ttl) { sets.incrementAndGet(); store.put(key, json); ttls.put(key, ttl); }
        @Override public void delete(String key) { deletes.incrementAndGet(); store.remove(key); }
    }

    /** {@link CacheBackend} that throws on every operation (simulates Redis down). */
    static final class ThrowingBackend implements CacheBackend {
        @Override public String get(String key) { throw new RuntimeException("redis down"); }
        @Override public void set(String key, String json, Duration ttl) { throw new RuntimeException("redis down"); }
        @Override public void delete(String key) { throw new RuntimeException("redis down"); }
    }

    private CacheService svc(CacheBackend backend) {
        return new CacheService(backend, mapper, props, new CacheMetrics());
    }

    // 1 & 3: cache hit returns stored value; loader NOT called on hit
    @Test
    void hitReturnsCachedAndSkipsLoader() {
        FakeBackend backend = new FakeBackend();
        backend.store.put("k", "{\"claimId\":99,\"status\":\"UNDER_REVIEW\"}");
        CacheService svc = svc(backend);
        AtomicInteger loads = new AtomicInteger();

        ClaimStub result = svc.getOrLoad("k", TYPE, TTL,
                () -> { loads.incrementAndGet(); return new ClaimStub(1L, "FRESH"); },
                v -> true);

        assertThat(result.claimId()).isEqualTo(99L);
        assertThat(result.status()).isEqualTo("UNDER_REVIEW");
        assertThat(loads.get()).isZero();
        assertThat(svc.metrics().hits()).isEqualTo(1);
    }

    // 2 & 4: cache miss → loader called once → cache populated
    @Test
    void missLoadsAndPopulatesCache() {
        FakeBackend backend = new FakeBackend();
        CacheService svc = svc(backend);
        AtomicInteger loads = new AtomicInteger();

        svc.getOrLoad("k", TYPE, TTL,
                () -> { loads.incrementAndGet(); return new ClaimStub(9L, "CLOSED"); },
                v -> true);

        assertThat(loads.get()).isEqualTo(1);
        assertThat(backend.store.get("k")).isEqualTo("{\"claimId\":9,\"status\":\"CLOSED\"}");
        assertThat(svc.metrics().misses()).isEqualTo(1);

        // second call is a hit, loader not re-invoked
        ClaimStub again = svc.getOrLoad("k", TYPE, TTL,
                () -> { loads.incrementAndGet(); return new ClaimStub(9L, "CLOSED"); },
                v -> true);
        assertThat(again.status()).isEqualTo("CLOSED");
        assertThat(loads.get()).isEqualTo(1);
        assertThat(svc.metrics().hits()).isEqualTo(1);
    }

    // 6: TTL forwarded to the backend
    @Test
    void ttlIsForwardedToBackend() {
        FakeBackend backend = new FakeBackend();
        CacheService svc = svc(backend);
        svc.getOrLoad("k", TYPE, Duration.ofSeconds(17),
                () -> new ClaimStub(1L, "x"), v -> true);
        assertThat(backend.ttls.get("k")).isEqualTo(Duration.ofSeconds(17));
    }

    // 7: evict removes the entry (invalidation)
    @Test
    void evictRemovesEntry() {
        FakeBackend backend = new FakeBackend();
        backend.store.put("k", "{\"claimId\":1,\"status\":\"OLD\"}");
        CacheService svc = svc(backend);
        svc.evict("k");
        assertThat(backend.store).doesNotContainKey("k");
        assertThat(backend.deletes.get()).isEqualTo(1);
    }

    // 8: write invalidation is a normal evict after the operation (covered here;
    //    the gateway/tool wiring is in the gateway tests)
    @Test
    void evictIsIdempotentWhenKeyAbsent() {
        FakeBackend backend = new FakeBackend();
        CacheService svc = svc(backend);
        svc.evict("missing");
        assertThat(backend.deletes.get()).isEqualTo(1); // safe, no throw
    }

    // 9: cache (Redis) unavailable → fail open → loader still runs, no throw
    @Test
    void cacheUnavailableFailsOpenToLoader() {
        CacheService svc = svc(new ThrowingBackend());
        AtomicInteger loads = new AtomicInteger();
        ClaimStub result = svc.getOrLoad("k", TYPE, TTL,
                () -> { loads.incrementAndGet(); return new ClaimStub(5L, "OK"); },
                v -> true);
        assertThat(result.claimId()).isEqualTo(5L);
        assertThat(loads.get()).isEqualTo(1);
        assertThat(svc.metrics().errors()).isGreaterThan(0);
    }

    // 10: backend unavailable → loader exception propagates (caller handles it)
    @Test
    void loaderFailurePropagatesToCaller() {
        CacheService svc = svc(new FakeBackend());
        assertThatThrownBy(() -> svc.getOrLoad("k", TYPE, TTL,
                () -> { throw new IllegalStateException("backend down"); },
                v -> true)).isInstanceOf(IllegalStateException.class);
    }

    // 11: serialization round-trip through the backend
    @Test
    void serializationRoundTrips() {
        FakeBackend backend = new FakeBackend();
        CacheService svc = svc(backend);
        svc.getOrLoad("k", LIST_TYPE, TTL,
                () -> List.of(new ClaimStub(1L, "A"), new ClaimStub(2L, "B")),
                v -> !v.isEmpty());
        List<ClaimStub> list = svc.get("k", LIST_TYPE);
        assertThat(list).extracting(ClaimStub::claimId).containsExactly(1L, 2L);
    }

    // 12: deterministic, collision-safe keys
    @Test
    void keysAreDeterministicAndNamespaced() {
        CacheService svc = svc(new FakeBackend());
        assertThat(svc.key("get_claim_status", "claim", 99L))
                .isEqualTo("agent:v1:get_claim_status:claim:99");
        assertThat(svc.key("get_claim_status", "claim", 99L))
                .isEqualTo(svc.key("get_claim_status", "claim", 99L));
        // different resource ids differ
        assertThat(svc.key("get_claim_status", "claim", 99L))
                .isNotEqualTo(svc.key("get_claim_status", "claim", 100L));
        // no claim PII in the key
        assertThat(svc.key("get_claim_status", "claim", 99L)).doesNotContain("UNDER_REVIEW");
    }

    // 14: resource isolation - different resources use different keys
    @Test
    void resourceIsolationByKey() {
        FakeBackend backend = new FakeBackend();
        CacheService svc = svc(backend);
        svc.getOrLoad("agent:v1:get_claim_status:claim:99", TYPE, TTL,
                () -> new ClaimStub(99L, "UNDER_REVIEW"), v -> true);
        ClaimStub other = svc.getOrLoad("agent:v1:get_claim_status:claim:100", TYPE, TTL,
                () -> new ClaimStub(100L, "CLOSED"), v -> true);
        assertThat(other.status()).isEqualTo("CLOSED"); // not claim 99's cached value
    }

    // 15: negative caching disabled - non-cacheable results are never stored
    @Test
    void nonCacheableResultIsNotStored() {
        FakeBackend backend = new FakeBackend();
        CacheService svc = svc(backend);
        svc.getOrLoad("k", TYPE, TTL,
                () -> new ClaimStub(9L, "UNAVAILABLE"), v -> false);
        assertThat(backend.store).doesNotContainKey("k");
    }

    // 19: empty result (null) is returned, not cached, no error
    @Test
    void nullResultIsReturnedAndNotCached() {
        FakeBackend backend = new FakeBackend();
        CacheService svc = svc(backend);
        ClaimStub result = svc.getOrLoad("k", TYPE, TTL, () -> null, v -> true);
        assertThat(result).isNull();
        assertThat(backend.store).doesNotContainKey("k");
    }

    // 20: exception handling on deserialize failure → fail open, no throw
    @Test
    void corruptCacheEntryFailsOpen() {
        FakeBackend backend = new FakeBackend();
        backend.store.put("k", "not-json{{{");
        CacheService svc = svc(backend);
        AtomicInteger loads = new AtomicInteger();
        ClaimStub result = svc.getOrLoad("k", TYPE, TTL,
                () -> { loads.incrementAndGet(); return new ClaimStub(3L, "RECOVERED"); },
                v -> true);
        assertThat(result.status()).isEqualTo("RECOVERED");
        assertThat(loads.get()).isEqualTo(1);
        assertThat(svc.metrics().errors()).isGreaterThan(0);
    }

    // 16 & 17: concurrent misses coalesce (single-flight) → loader called once
    @Test
    void concurrentMissesCoalesceToSingleLoad() throws Exception {
        FakeBackend backend = new FakeBackend();
        CacheService svc = svc(backend);
        AtomicInteger loads = new AtomicInteger();
        int n = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Thread t = new Thread(() -> {
                try { start.await(); } catch (InterruptedException ignored) {}
                svc.getOrLoad("k", TYPE, TTL,
                        () -> { loads.incrementAndGet(); sleep(20); return new ClaimStub(1L, "OK"); },
                        v -> true);
                done.countDown();
            });
            threads.add(t);
            t.start();
        }
        start.countDown();
        done.await();
        for (Thread t : threads) t.join();
        assertThat(loads.get()).isEqualTo(1);
    }

    // disabled cache → straight to loader, nothing stored
    @Test
    void disabledCacheBypassesStore() {
        props.setEnabled(false);
        FakeBackend backend = new FakeBackend();
        CacheService svc = svc(backend);
        ClaimStub result = svc.getOrLoad("k", TYPE, TTL, () -> new ClaimStub(7L, "OK"), v -> true);
        assertThat(result.status()).isEqualTo("OK");
        assertThat(backend.sets.get()).isZero();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}