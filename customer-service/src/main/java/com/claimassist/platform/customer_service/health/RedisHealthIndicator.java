package com.claimassist.platform.customer_service.health;

import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Redis connectivity.
 * Requires RedisTemplate to be available; will fail at startup if Redis is not configured.
 */
@Component("redisHealth")
@Slf4j
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisHealthIndicator(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Health health() {
        try {
            // Attempt a simple PING command
            var pong = redisTemplate.getConnectionFactory().getConnection().ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                return Health.up()
                        .withDetail("redis", "Connected")
                        .build();
            } else {
                return Health.down()
                        .withDetail("redis", "Unexpected response from PING")
                        .build();
            }
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return Health.down()
                    .withException(e)
                    .withDetail("issue", "Failed to connect to Redis")
                    .build();
        }
    }
}
