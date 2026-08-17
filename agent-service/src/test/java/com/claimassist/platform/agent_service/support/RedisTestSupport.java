package com.claimassist.platform.agent_service.support;

import com.claimassist.platform.agent_service.cache.CacheBackend;
import com.claimassist.platform.agent_service.cache.CacheMetrics;
import com.claimassist.platform.agent_service.cache.CacheService;
import com.claimassist.platform.agent_service.cache.RedisCacheBackend;
import com.claimassist.platform.agent_service.config.CacheProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

/**
 * Shared helpers for tests that require a REAL Redis instance. These tests are
 * skipped automatically (not failed) when Redis is not reachable, so the build
 * stays green on machines without Redis - mirroring {@link OllamaTestSupport}.
 */
public final class RedisTestSupport {

    public static final String HOST = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    public static final int PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

    private RedisTestSupport() {}

    /** Skip the test unless a Redis is actually reachable. */
    public static void assumeRedisAvailable() {
        Assumptions.assumeTrue(probe(), "Redis not reachable at " + HOST + ":" + PORT + " - skipping real-Redis test.");
    }

    private static boolean probe() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Build a real {@link StringRedisTemplate} wired to the configured Redis. */
    public static StringRedisTemplate redisTemplate() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(HOST, PORT);
        factory.setTimeout(2000);
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    /** A real cache service over the real Redis backend, on a clean namespace. */
    public static CacheService cacheService(String namespace) {
        CacheProperties props = new CacheProperties();
        props.setVersion(namespace);
        CacheBackend backend = new RedisCacheBackend(redisTemplate());
        return new CacheService(backend, new ObjectMapper(), props, new CacheMetrics());
    }

    public static CacheBackend redisBackend() {
        return new RedisCacheBackend(redisTemplate());
    }
}