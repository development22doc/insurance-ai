package com.claimassist.platform.claims_service.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Production-grade Cache-Aside pattern implementation.
 * Provides typed caching with TTL, eviction, and invalidation support.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheService {

    private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;

    // Cache TTLs (in seconds)
    public static final long CUSTOMER_CACHE_TTL = 300;      // 5 minutes
    public static final long POLICY_CACHE_TTL = 600;        // 10 minutes
    public static final long LOOKUP_CACHE_TTL = 3600;       // 1 hour
    public static final long CLAIM_STATUS_CACHE_TTL = 60;   // 1 minute (frequent updates)

    // Cache key prefixes
    private static final String CUSTOMER_PREFIX = "customer:";
    private static final String POLICY_PREFIX = "policy:";
    private static final String CLAIM_PREFIX = "claim:";
    private static final String LOOKUP_PREFIX = "lookup:";

    /**
     * Get a cached value by key, with type casting.
     * Returns null if key doesn't exist or has expired.
     *
     * @param key The cache key
     * @param type The expected return type
     * @return The cached value or null
     */
    public <T> T get(String key, Class<T> type) {
        try {
            RedisTemplate<String, Object> template = redisTemplateProvider.getIfAvailable();
            if (template == null) {
                log.debug("Cache MISS: {} (Redis not available)", key);
                return null;
            }
            Object value = template.opsForValue().get(key);
            if (value != null) {
                if (type.isInstance(value)) {
                    log.debug("Cache HIT: {}", key);
                    return type.cast(value);
                } else {
                    log.warn("Cache type mismatch: key={}, expected={}, actual={}",
                            key, type.getSimpleName(), value.getClass().getSimpleName());
                    delete(key);
                }
            } else {
                log.debug("Cache MISS: {}", key);
            }
            return null;
        } catch (Exception e) {
            log.error("Error retrieving from cache: {}", key, e);
            return null;
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
        try {
            RedisTemplate<String, Object> template = redisTemplateProvider.getIfAvailable();
            if (template == null) {
                log.debug("Cache SET SKIPPED: {} (Redis not available)", key);
                return;
            }
            if (value == null) {
                log.warn("Attempted to cache null value: {}", key);
                return;
            }
            template.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
            log.debug("Cache SET: key={}, ttl={}s", key, ttlSeconds);
        } catch (Exception e) {
            log.error("Error setting cache: {}", key, e);
        }
    }

    /**
     * Delete a key from cache.
     *
     * @param key The cache key
     */
    public void delete(String key) {
        try {
            RedisTemplate<String, Object> template = redisTemplateProvider.getIfAvailable();
            if (template == null) {
                log.debug("Cache DELETE SKIPPED: {} (Redis not available)", key);
                return;
            }
            template.delete(key);
            log.debug("Cache DELETE: {}", key);
        } catch (Exception e) {
            log.error("Error deleting from cache: {}", key, e);
        }
    }

    /**
     * Delete multiple keys from cache (pattern-based eviction).
     *
     * @param pattern The key pattern (e.g., "claim:123:*")
     */
    public void deleteByPattern(String pattern) {
        try {
            RedisTemplate<String, Object> template = redisTemplateProvider.getIfAvailable();
            if (template == null) {
                log.debug("Cache EVICT SKIPPED: pattern={} (Redis not available)", pattern);
                return;
            }
            var keys = template.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                template.delete(keys);
                log.debug("Cache EVICT: pattern={}, count={}", pattern, keys.size());
            }
        } catch (Exception e) {
            log.error("Error evicting cache by pattern: {}", pattern, e);
        }
    }

    /**
     * Invalidate all customer-related cache for a specific customer.
     *
     * @param customerId The customer ID
     */
    public void invalidateCustomerCache(Long customerId) {
        String pattern = CUSTOMER_PREFIX + customerId + ":*";
        deleteByPattern(pattern);
        log.info("Invalidated customer cache: customerId={}", customerId);
    }

    /**
     * Invalidate all policy-related cache for a specific customer.
     *
     * @param customerId The customer ID
     */
    public void invalidateCustomerPoliciesCache(Long customerId) {
        String pattern = POLICY_PREFIX + "customer:" + customerId + ":*";
        deleteByPattern(pattern);
        log.info("Invalidated policies cache: customerId={}", customerId);
    }

    /**
     * Invalidate all claim-related cache for a specific claim.
     *
     * @param claimId The claim ID
     */
    public void invalidateClaimCache(Long claimId) {
        String pattern = CLAIM_PREFIX + claimId + ":*";
        deleteByPattern(pattern);
        log.info("Invalidated claim cache: claimId={}", claimId);
    }

    /**
     * Invalidate all lookup cache (typically after system configuration changes).
     */
    public void invalidateLookupCache() {
        String pattern = LOOKUP_PREFIX + "*";
        deleteByPattern(pattern);
        log.info("Invalidated all lookup cache");
    }

    /**
     * Clear entire cache (use sparingly).
     */
    public void clearAll() {
        try {
            RedisTemplate<String, Object> template = redisTemplateProvider.getIfAvailable();
            if (template == null) {
                log.debug("Cache CLEAR SKIPPED (Redis not available)");
                return;
            }
            template.getConnectionFactory().getConnection().flushAll();
            log.warn("Cleared entire Redis cache");
        } catch (Exception e) {
            log.error("Error clearing cache", e);
        }
    }

    /**
     * Get number of keys in cache matching a pattern.
     *
     * @param pattern The key pattern
     * @return Number of matching keys
     */
    public long countKeysByPattern(String pattern) {
        try {
            RedisTemplate<String, Object> template = redisTemplateProvider.getIfAvailable();
            if (template == null) {
                log.debug("Cache COUNT SKIPPED: pattern={} (Redis not available)", pattern);
                return 0;
            }
            var keys = template.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("Error counting cache keys: {}", pattern, e);
            return -1;
        }
    }

    /**
     * Generate cache key for customer entity.
     *
     * @param customerId The customer ID
     * @return The cache key
     */
    public static String customerKey(Long customerId) {
        return CUSTOMER_PREFIX + customerId;
    }

    /**
     * Generate cache key for customer policies list.
     *
     * @param customerId The customer ID
     * @return The cache key
     */
    public static String customerPoliciesKey(Long customerId) {
        return POLICY_PREFIX + "customer:" + customerId;
    }

    /**
     * Generate cache key for policy entity.
     *
     * @param policyId The policy ID
     * @return The cache key
     */
    public static String policyKey(Long policyId) {
        return POLICY_PREFIX + policyId;
    }

    /**
     * Generate cache key for claim status.
     *
     * @param claimId The claim ID
     * @return The cache key
     */
    public static String claimStatusKey(Long claimId) {
        return CLAIM_PREFIX + claimId + ":status";
    }

    /**
     * Generate cache key for claim full entity.
     *
     * @param claimId The claim ID
     * @return The cache key
     */
    public static String claimKey(Long claimId) {
        return CLAIM_PREFIX + claimId;
    }

    /**
     * Generate cache key for lookup values.
     *
     * @param lookupType The lookup type (e.g., "incident-types")
     * @return The cache key
     */
    public static String lookupKey(String lookupType) {
        return LOOKUP_PREFIX + lookupType;
    }
}

