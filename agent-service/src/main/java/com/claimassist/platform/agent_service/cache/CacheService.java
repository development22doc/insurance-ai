package com.claimassist.platform.agent_service.cache;

import com.claimassist.platform.agent_service.config.CacheProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The agent's cache-aside layer. Cache is NOT a source of truth - the database /
 * backend services are. This layer only optimises reads:
 *
 * <pre>
 * Request → cache GET → HIT? → return
 *                        │ NO
 *                        ▼
 *              load from backend (source of truth)
 *                        ▼
 *              cache SET (only if cacheable)
 *                        ▼
 *                        return
 * </pre>
 *
 * <h2>Guarantees</h2>
 * <ul>
 *   <li><b>Fail-open:</b> any Redis/cache error is logged as controlled metadata
 *       and the request falls through to the source of truth. A cache outage can
 *       never take the agent down or lose a business answer.</li>
 *   <li><b>Single-flight:</b> concurrent cache misses for the same key coalesce
 *       on a per-key lock (double-checked), so a thundering herd on one key does
 *       not fan out to N backend calls.</li>
 *   <li><b>Never caches failures:</b> only values satisfying {@code cacheable}
 *       are stored (so UNAVAILABLE / NOT_FOUND placeholders are never cached).</li>
 *   <li><b>Thread-safe:</b> the backend (Spring Data Redis
 *       {@code StringRedisTemplate}) is thread-safe; this service is stateless
 *       apart from thread-safe maps and atomic metrics.</li>
 *   <li><b>Sensitive-data safe:</b> cache keys carry only version + operation +
 *       resource ids (no user/claim PII), and no cache content is ever logged.</li>
 * </ul>
 */
@Slf4j
@Service
public class CacheService {

    private final CacheBackend backend;
    private final ObjectMapper objectMapper;
    private final CacheProperties properties;
    private final CacheMetrics metrics;

    /** Per-key monitors used to coalesce concurrent misses (single-flight). */
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public CacheService(CacheBackend backend, ObjectMapper objectMapper,
                        CacheProperties properties, CacheMetrics metrics) {
        this.backend = backend;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metrics = metrics;
    }

    /** Build a deterministic, collision-safe cache key. */
    public String key(String operation, String resourceType, Long resourceId) {
        return "agent:" + properties.getVersion() + ":" + operation + ":" + resourceType + ":" + resourceId;
    }

    /**
     * Cache-aside read with single-flight coalescing and fail-open fallback.
     *
     * @param cacheable only results satisfying this predicate are stored (never
     *                  cache failures/placeholders)
     */
    public <T> T getOrLoad(String key, TypeReference<T> typeRef, Duration ttl,
                           Supplier<T> loader, Predicate<T> cacheable) {
        if (!properties.isEnabled()) {
            return loader.get();
        }

        // Fast hit path (no lock taken on a hit).
        T cached = readCached(key, typeRef);
        if (cached != null) {
            metrics.recordHit();
            return cached;
        }
        metrics.recordMiss();

        // Miss → single-flight on a per-key lock; re-check inside so a
        // concurrent miss that already loaded skips re-loading.
        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            T cachedAgain = readCached(key, typeRef);
            if (cachedAgain != null) {
                metrics.recordHit();
                return cachedAgain;
            }
            T value = loader.get();
            if (value != null && cacheable.test(value)) {
                put(key, value, ttl);
            }
            return value;
        }
    }

    /** Read-only cache GET. Returns {@code null} on miss or cache failure. */
    public <T> T get(String key, TypeReference<T> typeRef) {
        return readCached(key, typeRef);
    }

    /** Best-effort write-through put; never throws (fail-open). */
    public <T> void put(String key, T value, Duration ttl) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            backend.set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            metrics.recordError();
            log.warn("Cache PUT failed (key={}): {}", key, e.toString());
        }
    }

    /** Remove a key (e.g. after a successful write to invalidate read caches). */
    public void evict(String key) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            backend.delete(key);
        } catch (Exception e) {
            metrics.recordError();
            log.warn("Cache DELETE failed (key={}): {}", key, e.toString());
        }
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public CacheMetrics metrics() {
        return metrics;
    }

    private <T> T readCached(String key, TypeReference<T> typeRef) {
        try {
            String json = backend.get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            metrics.recordError();
            log.warn("Cache GET failed (key={}); falling back to source of truth: {}", key, e.toString());
            return null;
        }
    }
}