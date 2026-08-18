package com.claimassist.platform.agent_service.config;

import com.claimassist.platform.agent_service.cache.CacheBackend;
import com.claimassist.platform.agent_service.cache.CacheMetrics;
import com.claimassist.platform.agent_service.cache.CacheService;
import com.claimassist.platform.agent_service.cache.RedisCacheBackend;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CacheConfigTest {

    private final CacheConfig config = new CacheConfig();

    @Test
    void cacheBackendIsRedisBackendOverTemplate() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        CacheBackend backend = config.cacheBackend(template);
        assertThat(backend).isInstanceOf(RedisCacheBackend.class);
    }

    @Test
    void cacheMetricsIsFreshInstance() {
        assertThat(config.cacheMetrics()).isInstanceOf(CacheMetrics.class);
    }

    @Test
    void cacheServiceWiresDependencies() {
        CacheBackend backend = mock(CacheBackend.class);
        ObjectMapper objectMapper = new ObjectMapper();
        CacheProperties props = new CacheProperties();
        CacheMetrics metrics = new CacheMetrics();
        CacheService service = config.cacheService(backend, objectMapper, props, metrics);
        assertThat(service).isNotNull();
    }

    @Test
    void cachePropertiesDefaults() {
        CacheProperties props = new CacheProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getVersion()).isEqualTo("v1");
        assertThat(props.getClaimStatusTtl()).isEqualTo(Duration.ofSeconds(30));
        assertThat(props.getPolicyCoverageTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(props.getClaimDocumentsTtl()).isEqualTo(Duration.ofMinutes(2));
    }
}