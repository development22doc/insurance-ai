package com.claimassist.platform.agent_service.integration;

import com.claimassist.platform.agent_service.cache.CacheMetrics;
import com.claimassist.platform.agent_service.cache.CacheService;
import com.claimassist.platform.agent_service.cache.RedisCacheBackend;
import com.claimassist.platform.agent_service.config.CacheProperties;
import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.agent_service.client.ClaimsClient;
import com.claimassist.platform.agent_service.support.RedisTestSupport;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REAL Redis integration tests of the cache boundary. Verify:
 * miss → backend → populate → hit → no backend → write → invalidate → fresh
 * read, plus the mandatory cache-failure fallback (Redis down → backend still
 * runs → correct result). Skipped when no Redis is reachable.
 */
@Tag("redis")
class AgentCacheRedisIntegrationTest {

    private static final TypeReference<ClaimStatusDto> TYPE = new TypeReference<ClaimStatusDto>() {};

    @Test
    void missThenHitThenInvalidateThenFreshRead() {
        RedisTestSupport.assumeRedisAvailable();
        CacheService cache = RedisTestSupport.cacheService("it-" + System.nanoTime());
        AtomicInteger backendCalls = new AtomicInteger();
        String key = cache.key("get_claim_status", "claim", 4242L);

        // FIRST REQUEST → miss → backend called → populated
        ClaimStatusDto first = cache.getOrLoad(key, TYPE, Duration.ofMinutes(5),
                () -> { backendCalls.incrementAndGet(); return status(4242L, "UNDER_REVIEW"); },
                v -> v != null && !"UNAVAILABLE".equals(v.status()));
        assertThat(first.status()).isEqualTo("UNDER_REVIEW");
        assertThat(backendCalls.get()).isEqualTo(1);
        assertThat(cache.metrics().misses()).isEqualTo(1);

        // SECOND REQUEST → hit → backend NOT called
        ClaimStatusDto second = cache.getOrLoad(key, TYPE, Duration.ofMinutes(5),
                () -> { backendCalls.incrementAndGet(); return status(4242L, "CHANGED"); },
                v -> v != null);
        assertThat(second.status()).isEqualTo("UNDER_REVIEW"); // from cache
        assertThat(backendCalls.get()).isEqualTo(1);
        assertThat(cache.metrics().hits()).isEqualTo(1);

        // WRITE → invalidate → NEXT READ → miss → fresh backend data
        cache.evict(key);
        ClaimStatusDto third = cache.getOrLoad(key, TYPE, Duration.ofMinutes(5),
                () -> { backendCalls.incrementAndGet(); return status(4242L, "CLOSED"); },
                v -> v != null);
        assertThat(third.status()).isEqualTo("CLOSED"); // fresh backend data
        assertThat(backendCalls.get()).isEqualTo(2);
    }

    @Test
    void serializationRoundTripsThroughRealRedis() {
        RedisTestSupport.assumeRedisAvailable();
        CacheService cache = RedisTestSupport.cacheService("it-" + System.nanoTime());
        String key = cache.key("get_claim_status", "claim", 7L);
        cache.put(key, status(7L, "APPROVED"), Duration.ofMinutes(5));
        ClaimStatusDto loaded = cache.get(key, TYPE);
        assertThat(loaded).isEqualTo(status(7L, "APPROVED"));
    }

    @Test
    void resourceIsolationAcrossClaims() {
        RedisTestSupport.assumeRedisAvailable();
        CacheService cache = RedisTestSupport.cacheService("it-" + System.nanoTime());
        cache.put(cache.key("get_claim_status", "claim", 1L), status(1L, "A"), Duration.ofMinutes(5));
        ClaimStatusDto two = cache.get(cache.key("get_claim_status", "claim", 2L), TYPE);
        assertThat(two).isNull(); // claim 2 must not see claim 1's cached entry
    }

    @Test
    void gatewayCacheBoundaryBackendNotCalledOnHit() {
        RedisTestSupport.assumeRedisAvailable();
        String ns = "it-gw-" + System.nanoTime();
        CacheProperties props = new CacheProperties();
        props.setVersion(ns);
        CacheService cache = new CacheService(RedisTestSupport.redisBackend(),
                new ObjectMapper(), props, new CacheMetrics());
        ClaimsClient client = mock(ClaimsClient.class);
        when(client.getClaimStatus(99L)).thenReturn(status(99L, "UNDER_REVIEW"));
        ClaimsServiceGateway gateway = new ClaimsServiceGateway(client, cache, props);

        gateway.getClaimStatus(99L); // miss → backend
        gateway.getClaimStatus(99L); // hit
        org.mockito.Mockito.verify(client, org.mockito.Mockito.atMost(1)).getClaimStatus(99L);
    }

    // MANDATORY: Redis unavailable → cache op fails → backend still executes → correct result
    @Test
    void cacheFailureFailsOpenToBackend() {
        // Point the backend at a Redis that is not listening → ops throw.
        LettuceConnectionFactory dead = new LettuceConnectionFactory(
                RedisTestSupport.HOST, RedisTestSupport.PORT == 6379 ? 6378 : RedisTestSupport.PORT);
        dead.setTimeout(500);
        dead.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(dead);
        template.afterPropertiesSet();
        CacheService cache = new CacheService(new RedisCacheBackend(template),
                new ObjectMapper(), new CacheProperties(), new CacheMetrics());

        AtomicInteger backendCalls = new AtomicInteger();
        ClaimStatusDto result = cache.getOrLoad("k", TYPE, Duration.ofSeconds(5),
                () -> { backendCalls.incrementAndGet(); return status(1L, "OK"); },
                v -> v != null);

        assertThat(result.status()).isEqualTo("OK"); // source of truth still answered
        assertThat(backendCalls.get()).isEqualTo(1);
        assertThat(cache.metrics().errors()).isGreaterThan(0);
    }

    private static ClaimStatusDto status(Long id, String s) {
        return new ClaimStatusDto(id, 7L, "CLM-" + id, s, "FIRE", 1000L, null, List.of());
    }
}