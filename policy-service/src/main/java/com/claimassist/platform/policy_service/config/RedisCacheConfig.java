package com.claimassist.platform.policy_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
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

    public static final String PRODUCTS_CACHE = "policyProducts";
    public static final String PRODUCT_DETAILS_CACHE = "policyProductDetails";
    public static final String PLAN_DETAILS_CACHE = "policyPlanDetails";
    public static final String CUSTOMER_POLICIES_CACHE = "customerPolicyList";
    public static final String CUSTOMER_POLICY_DETAILS_CACHE = "customerPolicyDetail";
    public static final String POLICY_CONTRACTS_CACHE = "policyContractList";
    public static final String POLICY_CONTRACT_DETAILS_CACHE = "policyContractDetail";
    public static final String POLICY_PERIOD_HISTORY_CACHE = "policyPeriodHistory";
    public static final String POLICY_CURRENT_PERIOD_CACHE = "policyCurrentPeriod";

    private static final Duration PRODUCTS_TTL = Duration.ofMinutes(15);
    private static final Duration PRODUCT_DETAILS_TTL = Duration.ofMinutes(15);
    private static final Duration PLAN_DETAILS_TTL = Duration.ofMinutes(15);
    private static final Duration CUSTOMER_POLICIES_TTL = Duration.ofMinutes(10);
    private static final Duration CUSTOMER_POLICY_DETAILS_TTL = Duration.ofMinutes(10);
    private static final Duration POLICY_CONTRACTS_TTL = Duration.ofMinutes(10);
    private static final Duration POLICY_CONTRACT_DETAILS_TTL = Duration.ofMinutes(10);
    private static final Duration POLICY_PERIOD_HISTORY_TTL = Duration.ofMinutes(10);
    private static final Duration POLICY_CURRENT_PERIOD_TTL = Duration.ofMinutes(5);
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        JsonMapper.Builder mapperBuilder = JsonMapper.builder().addModule(new JavaTimeModule());
        ObjectMapper objectMapper = mapperBuilder.build();
        objectMapper = objectMapper.copy().activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.EVERYTHING
        );

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer cacheManagerCustomizer() {
        JsonMapper.Builder mapperBuilder = JsonMapper.builder().addModule(new JavaTimeModule());
        ObjectMapper objectMapper = mapperBuilder.build();
        objectMapper = objectMapper.copy().activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.EVERYTHING
        );

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        return builder -> builder
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration(PRODUCTS_CACHE, defaultConfig.entryTtl(PRODUCTS_TTL))
                .withCacheConfiguration(PRODUCT_DETAILS_CACHE, defaultConfig.entryTtl(PRODUCT_DETAILS_TTL))
                .withCacheConfiguration(PLAN_DETAILS_CACHE, defaultConfig.entryTtl(PLAN_DETAILS_TTL))
                .withCacheConfiguration(CUSTOMER_POLICIES_CACHE, defaultConfig.entryTtl(CUSTOMER_POLICIES_TTL))
                .withCacheConfiguration(CUSTOMER_POLICY_DETAILS_CACHE, defaultConfig.entryTtl(CUSTOMER_POLICY_DETAILS_TTL))
                .withCacheConfiguration(POLICY_CONTRACTS_CACHE, defaultConfig.entryTtl(POLICY_CONTRACTS_TTL))
                .withCacheConfiguration(POLICY_CONTRACT_DETAILS_CACHE, defaultConfig.entryTtl(POLICY_CONTRACT_DETAILS_TTL))
                .withCacheConfiguration(POLICY_PERIOD_HISTORY_CACHE, defaultConfig.entryTtl(POLICY_PERIOD_HISTORY_TTL))
                .withCacheConfiguration(POLICY_CURRENT_PERIOD_CACHE, defaultConfig.entryTtl(POLICY_CURRENT_PERIOD_TTL));
    }

    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Redis cache GET failed for cache={} key={} - falling back to source of truth", cache.getName(), key, e);
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Redis cache PUT failed for cache={} key={} - DB result returned without caching", cache.getName(), key, e);
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Redis cache EVICT failed for cache={} key={} - stale entry bounded by TTL", cache.getName(), key, e);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Redis cache CLEAR failed for cache={} - stale entries bounded by TTL", cache.getName(), e);
            }
        };
    }
}
