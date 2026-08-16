package com.claimassist.platform.agent_service.cache;

import java.time.Duration;

/**
 * Minimal, purpose-built boundary between {@link CacheService} (the cache-aside
 * logic) and whatever Redis store is configured. This keeps the cache-aside
 * logic pure and unit-testable with a stub backend, while the real
 * {@link RedisCacheBackend} is exercised against an actual Redis in the
 * integration tests. It intentionally exposes only the three operations the
 * cache-aside pattern needs - no generic key/value kitchen-sink.
 */
public interface CacheBackend {

    /** Fetch the JSON value for {@code key}, or {@code null} if absent. */
    String get(String key);

    /** Store {@code json} at {@code key} with an expiry. */
    void set(String key, String json, Duration ttl);

    /** Remove {@code key}. Safe to call when the key does not exist. */
    void delete(String key);
}