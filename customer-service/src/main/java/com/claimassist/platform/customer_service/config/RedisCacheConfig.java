package com.claimassist.platform.customer_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
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
 * customer-service's only real hot read path is InternalCustomerController's
 * getPolicyCoverage - it's called by agent-service on essentially every
 * agent chat turn that touches coverage, and by claims-service on every
 * claim submission. Deductible/limit/product-type change at most a couple of
 * times a policy's lifetime (renewal), so a short TTL cache turns a very
 * frequent DB read into a Redis hit for the overwhelming majority of calls,
 * at negligible staleness risk (5-minute TTL - see POLICY_COVERAGE_CACHE
 * below; MY_POLICIES_CACHE gets a shorter 2-minute TTL since it's a listing
 * view users check right after an action, e.g. right after filing a claim).
 * See PolicyQueryService for the @Cacheable method and CAFFEINE-vs-REDIS
 * note below.
 * <p>
 * Redis (not a local/Caffeine cache) is deliberate: customer-service runs as
 * multiple replicas behind the gateway/Eureka, and a local cache would mean
 * a policy status change is only reflected on whichever replica happens to
 * have evicted its stale entry - Redis gives every replica the same view and
 * the same eviction the instant a write happens.
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisCacheConfig {

    public static final String POLICY_COVERAGE_CACHE = "policyCoverage";
    public static final String MY_POLICIES_CACHE = "myPolicies";
    public static final String CUSTOMER_LOOKUP_CACHE = "customerLookup";
    public static final String REFERENCE_DATA_CACHE = "referenceData";

    @Bean
    public RedisCacheManagerBuilderCustomizer cacheManagerCustomizer(ObjectMapper objectMapper) {
        var serializer = new GenericJackson2JsonRedisSerializer(
                JsonMapper.builder().addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).build());

        return builder -> builder
                .withCacheConfiguration(POLICY_COVERAGE_CACHE, baseConfig(serializer, Duration.ofMinutes(5)))
                .withCacheConfiguration(MY_POLICIES_CACHE, baseConfig(serializer, Duration.ofMinutes(2)))
                .withCacheConfiguration(CUSTOMER_LOOKUP_CACHE, baseConfig(serializer, Duration.ofMinutes(10)))
                .withCacheConfiguration(REFERENCE_DATA_CACHE, baseConfig(serializer, Duration.ofMinutes(30)));
    }

    private RedisCacheConfiguration baseConfig(GenericJackson2JsonRedisSerializer serializer, Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }

    /**
     * Without this, Spring's default CacheErrorHandler lets a Redis
     * connection failure propagate out of an @Cacheable/@CacheEvict method -
     * meaning a Redis outage would turn a working DB-backed read into a hard
     * 500, which defeats the entire point of caching being a performance
     * optimization layered on top of the database, not a dependency the
     * request path can't survive without. This logs the failure (so an
     * outage is still visible/alertable) and then falls through to the
     * underlying method - a cache miss, functionally - instead of failing
     * the request. Applies to every cache in this service.
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
                // Unlike get/put, a failed evict can leave genuinely stale data behind rather than
                // just a missed optimization - re-throwing (via the default handler) surfaces that
                // loudly instead of silently swallowing it.
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
