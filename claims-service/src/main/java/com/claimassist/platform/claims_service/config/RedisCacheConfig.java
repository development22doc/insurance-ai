package com.claimassist.platform.claims_service.config;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * claim status + history is read by InternalClaimsController on EVERY
 * agent-service get_claim_status tool call (i.e. most agent chat turns) and
 * by GET /claims/{id}. Unlike policy coverage, claim status changes fairly
 * often (an adjuster actively working a claim), so the TTL here is short -
 * this cache exists mainly to absorb bursts (a customer re-asking "what's my
 * status" three times in one conversation, or the agent re-checking after a
 * tool call) rather than to serve long-stale data. The real correctness
 * guarantee comes from the @CacheEvict wired into
 * ClaimCommandServiceImpl.applyStatusChange - the SAME method used by both
 * the REST path and the AI-agent saga path, so a status change is never
 * visible-but-uncached or cached-but-stale from either entry point.
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisCacheConfig {

    public static final String CLAIM_STATUS_CACHE = "claimStatus";
    public static final String CLAIM_PERMISSION_LOOKUP_CACHE = "claimPermissionLookup";

    @Bean
    public RedisCacheManagerBuilderCustomizer cacheManagerCustomizer() {
        var serializer = new GenericJackson2JsonRedisSerializer(
                JsonMapper.builder().addModule(new JavaTimeModule()).build());

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(30))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        RedisCacheConfiguration permissionLookupConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        return builder -> builder
                .withCacheConfiguration(CLAIM_STATUS_CACHE, config)
                .withCacheConfiguration(CLAIM_PERMISSION_LOOKUP_CACHE, permissionLookupConfig);
    }

    /**
     * Without this, a Redis outage would make @Cacheable(CLAIM_STATUS_CACHE)
     * and the @CacheEvict in ClaimCommandServiceImpl.applyStatusChange throw
     * straight out of the request path, turning a Redis blip into a hard
     * 500 on claim-status reads/updates. Logs and falls through to the
     * underlying method/DB instead - see the identical bean in
     * customer-service's RedisCacheConfig for the fuller rationale.
     * Evict/clear failures are still re-thrown (via the default handler)
     * since a failed evict can leave genuinely stale data behind, not just
     * a missed optimization.
     */
    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        SimpleCacheErrorHandler fallback = new SimpleCacheErrorHandler();
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis GET failed for cache={} key={}. Falling through to the underlying method.",
                        cache.getName(), key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis PUT failed for cache={} key={}. Continuing without caching this value.",
                        cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.error("Redis EVICT failed for cache={} key={}. A stale entry may remain until its TTL expires.",
                        cache.getName(), key, exception);
                fallback.handleCacheEvictError(exception, cache, key);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.error("Redis CLEAR failed for cache={}.", cache.getName(), exception);
                fallback.handleCacheClearError(exception, cache);
            }
        };
    }
}
