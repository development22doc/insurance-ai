package com.claimassist.platform.agent_service.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisCacheBackendTest {

    @Test
    void getDelegatesToRedisValueOperations() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        when(ops.get("k")).thenReturn("v");

        RedisCacheBackend backend = new RedisCacheBackend(template);
        assertThat(backend.get("k")).isEqualTo("v");
        verify(ops).get("k");
    }

    @Test
    void setDelegatesWithTtl() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);

        RedisCacheBackend backend = new RedisCacheBackend(template);
        Duration ttl = Duration.ofSeconds(60);
        backend.set("k", "json", ttl);
        verify(ops).set("k", "json", ttl);
    }

    @Test
    void deleteDelegatesToTemplate() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisCacheBackend backend = new RedisCacheBackend(template);
        backend.delete("k");
        verify(template).delete("k");
    }
}