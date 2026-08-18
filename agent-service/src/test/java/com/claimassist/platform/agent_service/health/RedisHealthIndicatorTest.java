package com.claimassist.platform.agent_service.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class RedisHealthIndicatorTest {

    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

    @Test
    void upWhenPingReturnsPong() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn("PONG");

        Health health = new RedisHealthIndicator(redisTemplate).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("redis", "Connected");
    }

    @Test
    void downWhenPingReturnsUnexpectedResponse() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn("PONG_WEIRD");

        Health health = new RedisHealthIndicator(redisTemplate).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void downWhenPingThrows() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenThrow(new IllegalStateException("conn refused"));

        Health health = new RedisHealthIndicator(redisTemplate).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("issue", "Failed to connect to Redis");
    }
}
