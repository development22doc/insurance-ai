package com.claimassist.platform.customer_service.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisHealthIndicatorTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    private final RedisHealthIndicator indicator = new RedisHealthIndicator(redisTemplate);

    @Test
    void health_pong_up() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn("PONG");

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails()).containsEntry("redis", "Connected");
    }

    @Test
    void health_unexpectedResponse_down() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn("NOPE");

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
    }

    @Test
    void health_exception_downWithIssueDetail() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenThrow(new IllegalStateException("no connection"));

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
        assertThat(health.getDetails()).containsEntry("issue", "Failed to connect to Redis");
    }
}