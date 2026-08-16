package com.claimassist.platform.agent_service.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis implementation of {@link CacheBackend} over Spring Data Redis's
 * thread-safe {@link StringRedisTemplate} (Lettuce). Values are stored as JSON
 * strings; serialization/deserialization stays in {@link CacheService} via
 * Jackson, so no Java-native serialization is used.
 * <p>
 * This backend is intentionally thin - all failure handling (fail-open to the
 * source of truth) lives in {@link CacheService}.
 */
@Component
public class RedisCacheBackend implements CacheBackend {

    private final StringRedisTemplate redisTemplate;

    public RedisCacheBackend(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void set(String key, String json, Duration ttl) {
        redisTemplate.opsForValue().set(key, json, ttl);
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }
}