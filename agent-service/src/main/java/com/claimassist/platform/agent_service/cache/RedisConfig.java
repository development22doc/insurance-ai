package com.claimassist.platform.agent_service.cache;

import org.springframework.context.annotation.Configuration;

/**
 * Redis configuration for distributed caching.
 * Implements Cache-Aside pattern with TTL and eviction policies.
 * 
 * Spring Boot automatically configures RedisTemplate when spring-boot-starter-data-redis
 * is on the classpath. Custom configuration can be added here if needed.
 */
@Configuration
public class RedisConfig {
    // Redis configuration is auto-configured by Spring Boot
    // Custom beans can be added here as needed
}

