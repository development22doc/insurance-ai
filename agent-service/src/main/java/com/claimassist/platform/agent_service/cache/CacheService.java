package com.claimassist.platform.agent_service.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Production-grade Cache-Aside pattern implementation for agent-service.
 * Provides typed caching with TTL, eviction, and invalidation support.
 */
@Component
@Slf4j
// Redis is optional for local developer startup; inject it only when available
// (use autowired(required=false) below)
public class CacheService {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PerformanceLogger performanceLogger;

    // Cache TTLs (in seconds)
    public static final long SESSION_CACHE_TTL = 1800;     // 30 minutes
    public static final long AGENT_EVENT_CACHE_TTL = 300;  // 5 minutes
    public static final long LLM_RESPONSE_CACHE_TTL = 3600; // 1 hour

    // Cache key prefixes
    private static final String SESSION_PREFIX = "agent:session:";
    private static final String EVENT_PREFIX = "agent:event:";
    private static final String LLM_PREFIX = "agent:llm:";

    /**
     * Get a cached value by key, with type casting.
     *
     * @param key The cache key
     * @param type The expected return type
     * @return The cached value or null
     */
    public <T> T get(String key, Class<T> type) {
        long start = System.nanoTime();
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                if (type.isInstance(value)) {
                    log.debug("Cache HIT: {}", key);
                    return type.cast(value);
                }
            } else {
                log.debug("Cache MISS: {}", key);
            }
            return null;
        } catch (Exception e) {
            log.error("Error retrieving from cache: {}", key, e);
            return null;
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            try { performanceLogger.log("CACHE", "cache.get", elapsedMs, java.util.Map.of("key", key)); } catch (Exception ignored) {}
        }
    }

    /**
     * Set a value in cache with specified TTL.
     *
     * @param key The cache key
     * @param value The value to cache
     * @param ttlSeconds Time to live in seconds
     */
    public void set(String key, Object value, long ttlSeconds) {
        long start = System.nanoTime();
        try {
            if (value == null) {
                log.warn("Attempted to cache null value: {}", key);
                return;
            }
            redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
            log.debug("Cache SET: key={}, ttl={}s", key, ttlSeconds);
        } catch (Exception e) {
            log.error("Error setting cache: {}", key, e);
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            try { performanceLogger.log("CACHE", "cache.set", elapsedMs, java.util.Map.of("key", key)); } catch (Exception ignored) {}
        }
    }

    /**
     * Delete a key from cache.
     *
     * @param key The cache key
     */
    public void delete(String key) {
        long start = System.nanoTime();
        try {
            redisTemplate.delete(key);
            log.debug("Cache DELETE: {}", key);
        } catch (Exception e) {
            log.error("Error deleting from cache: {}", key, e);
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            try { performanceLogger.log("CACHE", "cache.delete", elapsedMs, java.util.Map.of("key", key)); } catch (Exception ignored) {}
        }
    }

    /**
     * Delete multiple keys from cache (pattern-based eviction).
     *
     * @param pattern The key pattern (e.g., "agent:session:user:123:*")
     */
    public void deleteByPattern(String pattern) {
        long start = System.nanoTime();
        try {
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Cache EVICT: pattern={}, count={}", pattern, keys.size());
            }
        } catch (Exception e) {
            log.error("Error evicting cache by pattern: {}", pattern, e);
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            try { performanceLogger.log("CACHE", "cache.evict.pattern", elapsedMs, java.util.Map.of("pattern", pattern)); } catch (Exception ignored) {}
        }
    }

    /**
     * Invalidate all sessions for a specific user.
     *
     * @param userId The user ID
     */
    public void invalidateUserSessions(Long userId) {
        String pattern = SESSION_PREFIX + userId + ":*";
        deleteByPattern(pattern);
        log.info("Invalidated user sessions: userId={}", userId);
    }

    /**
     * Invalidate all events for a specific user.
     *
     * @param userId The user ID
     */
    public void invalidateUserEvents(Long userId) {
        String pattern = EVENT_PREFIX + userId + ":*";
        deleteByPattern(pattern);
        log.info("Invalidated user events: userId={}", userId);
    }

    /**
     * Generate cache key for session.
     *
     * @param userId The user ID
     * @param sessionId The session ID
     * @return The cache key
     */
    public static String sessionKey(Long userId, String sessionId) {
        return SESSION_PREFIX + userId + ":" + sessionId;
    }

    /**
     * Generate cache key for agent event.
     *
     * @param eventId The event ID
     * @return The cache key
     */
    public static String eventKey(String eventId) {
        return EVENT_PREFIX + eventId;
    }

    /**
     * Generate cache key for LLM response.
     *
     * @param prompt The prompt hash
     * @return The cache key
     */
    public static String llmResponseKey(String prompt) {
        return LLM_PREFIX + prompt;
    }

    /**
     * Clear entire cache (use sparingly).
     */
    public void clearAll() {
        long start = System.nanoTime();
        try {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
            log.warn("Cleared entire Redis cache");
        } catch (Exception e) {
            log.error("Error clearing cache", e);
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            try { performanceLogger.log("CACHE", "cache.clearAll", elapsedMs, java.util.Map.of()); } catch (Exception ignored) {}
        }
    }
}

