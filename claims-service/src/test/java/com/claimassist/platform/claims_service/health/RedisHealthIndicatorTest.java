package com.claimassist.platform.claims_service.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisHealthIndicatorTest {

    @Test
    void upOnPong() {
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        when(template.execute(any(RedisCallback.class))).thenAnswer(inv -> {
            RedisCallback<Object> cb = inv.getArgument(0);
            return cb instanceof RedisCallback ? (Object) "PONG" : null;
        });

        Health health = new RedisHealthIndicator(template).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("redis", "Connected");
    }

    @Test
    void downWhenPingReturnsUnexpected() {
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        when(template.execute(any(RedisCallback.class))).thenAnswer(inv -> {
            RedisCallback<Object> cb = inv.getArgument(0);
            return cb instanceof RedisCallback ? (Object) "PING" : null;
        });

        Health health = new RedisHealthIndicator(template).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("redis", "Unexpected response from PING");
    }

    @Test
    void downWhenExecutionThrows() {
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        when(template.execute(any(RedisCallback.class))).thenThrow(new RuntimeException("conn refused"));

        Health health = new RedisHealthIndicator(template).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("issue", "Failed to connect to Redis");
        verify(template).execute(any(RedisCallback.class));
    }
}