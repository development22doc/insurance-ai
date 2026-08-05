package com.claimassist.platform.claims_service.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Redis configuration for distributed caching.
 * Implements Cache-Aside pattern with TTL and eviction policies.
 *
 * Spring Boot automatically configures RedisTemplate when spring-boot-starter-data-redis
 * is on the classpath. Custom configuration can be added here if needed.
 */
@Configuration
public class RedisConfig {
    // Redis configuration: provide a RedisConnectionFactory and RedisTemplate
    // if Spring Boot did not auto-configure them (ensures health checks and
    // other Redis consumers have beans available at runtime).

    @Autowired
    private Environment env;

    @Bean
    @ConditionalOnMissingBean
    public RedisConnectionFactory redisConnectionFactory() {
        String host = env.getProperty("spring.data.redis.host", "localhost");
        int port = Integer.parseInt(env.getProperty("spring.data.redis.port", "6379"));
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        return new LettuceConnectionFactory(config);
    }

    @Bean(name = "redisTemplate")
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        return template;
    }
}

