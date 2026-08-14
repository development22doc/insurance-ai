package com.claimassist.platform.claims_service.integration;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Test-only beans for external infrastructure not required by PostgreSQL/JPA integration tests.
 */
@TestConfiguration
public class PostgresIntegrationTestConfig {

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
        return Mockito.mock(JwtDecoder.class);
    }

    @Bean
    @Primary
    RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }

    @Bean
    @Primary
    CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                com.claimassist.platform.claims_service.config.RedisCacheConfig.CLAIM_STATUS_CACHE,
                com.claimassist.platform.claims_service.config.RedisCacheConfig.CLAIM_PERMISSION_LOOKUP_CACHE);
    }
}
