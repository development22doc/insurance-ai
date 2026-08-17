package com.claimassist.platform.agent_service.config;

import com.claimassist.platform.agent_service.cache.CacheBackend;
import com.claimassist.platform.agent_service.cache.CacheMetrics;
import com.claimassist.platform.agent_service.cache.CacheService;
import com.claimassist.platform.agent_service.cache.RedisCacheBackend;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Wires the Phase 5 cache layer. Uses the existing project-standard Spring Data
 * Redis ({@link StringRedisTemplate} + Lettuce) - no second Redis client and no
 * custom abstraction beyond the thin {@link CacheBackend} boundary. Redis is
 * purely an optimisation layer; the database / backend services remain the
 * source of truth.
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    @Bean
    public CacheBackend cacheBackend(StringRedisTemplate redisTemplate) {
        return new RedisCacheBackend(redisTemplate);
    }

    @Bean
    public CacheMetrics cacheMetrics() {
        return new CacheMetrics();
    }

    @Bean
    public CacheService cacheService(CacheBackend cacheBackend, ObjectMapper objectMapper,
                                    CacheProperties cacheProperties, CacheMetrics cacheMetrics) {
        return new CacheService(cacheBackend, objectMapper, cacheProperties, cacheMetrics);
    }
}