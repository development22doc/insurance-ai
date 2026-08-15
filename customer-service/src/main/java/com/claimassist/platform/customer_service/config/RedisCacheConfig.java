package com.claimassist.platform.customer_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
@Slf4j
public class RedisCacheConfig {

    public static final String POLICY_COVERAGE_CACHE = "policyCoverage";
    public static final String MY_POLICIES_CACHE = "myPolicies";
    public static final String CUSTOMER_LOOKUP_CACHE = "customerLookup";
    public static final String REFERENCE_DATA_CACHE = "referenceData";

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
        RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        JsonMapper.Builder mapperBuilder = JsonMapper.builder()
                .addModule(new JavaTimeModule());

        ObjectMapper objectMapper = mapperBuilder.build();
        objectMapper = objectMapper.copy()
                .activateDefaultTyping(
                    objectMapper.getPolymorphicTypeValidator(),
                    ObjectMapper.DefaultTyping.NON_FINAL
                );

        GenericJackson2JsonRedisSerializer serializer =
            new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();

        return template;
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer cacheManagerCustomizer() {

        JsonMapper.Builder mapperBuilder = JsonMapper.builder()
                .addModule(new JavaTimeModule());

        ObjectMapper objectMapper = mapperBuilder.build();
        objectMapper = objectMapper.copy()
                .activateDefaultTyping(
                    objectMapper.getPolymorphicTypeValidator(),
                    ObjectMapper.DefaultTyping.NON_FINAL
                );

        GenericJackson2JsonRedisSerializer serializer =
            new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig =
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(
                    RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer));

        return builder -> builder
            .cacheDefaults(defaultConfig)
            .withCacheConfiguration(
                POLICY_COVERAGE_CACHE,
                defaultConfig.entryTtl(Duration.ofMinutes(10))
            );
    }

    /**
     * Fail-open cache error handler.
     * <p>
     * Redis is a cache, not a source of truth. If it is down, slow, or a
     * serialization/deserialization fails, the cached read path MUST fall back
     * to the database and the application MUST continue, rather than failing the
     * request (fail-closed). This handler logs the error and swallows it so the
     * Spring cache interceptor treats the failed GET as a miss (method runs
     * against the DB) and a failed PUT/EVICT as a no-op (the DB result is still
     * returned). Staleness from an evict that could not reach a recovering Redis
     * is bounded by each cache's TTL.
     */
    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Redis cache GET failed for cache={} key={} - falling back to source of truth",
                        cache.getName(), key, e);
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Redis cache PUT failed for cache={} key={} - DB result returned without caching",
                        cache.getName(), key, e);
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Redis cache EVICT failed for cache={} key={} - stale entry bounded by TTL",
                        cache.getName(), key, e);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Redis cache CLEAR failed for cache={} - stale entries bounded by TTL",
                        cache.getName(), e);
            }
        };
    }
}
