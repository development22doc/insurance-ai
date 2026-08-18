package com.claimassist.platform.agent_service.service.gateway;

import com.claimassist.platform.agent_service.cache.CacheBackend;
import com.claimassist.platform.agent_service.cache.CacheMetrics;
import com.claimassist.platform.agent_service.cache.CacheService;
import com.claimassist.platform.agent_service.client.CustomerClient;
import com.claimassist.platform.agent_service.config.CacheProperties;
import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerServiceGatewayCacheTest {

    static final class MemBackend implements CacheBackend {
        final Map<String, String> store = new ConcurrentHashMap<>();
        @Override public String get(String k) { return store.get(k); }
        @Override public void set(String k, String v, Duration t) { store.put(k, v); }
        @Override public void delete(String k) { store.remove(k); }
    }

    private CustomerServiceGateway gateway(CustomerClient client, MemBackend backend) {
        CacheProperties props = new CacheProperties();
        CacheService cache = new CacheService(backend, new ObjectMapper(), props, new CacheMetrics());
        return new CustomerServiceGateway(client, cache, props);
    }

    private final PolicyCoverageDto coverage =
            new PolicyCoverageDto(7L, "POL-7", "ACTIVE", "HOME", "Comprehensive", 500L, 1000000L, "2027-01-01");

    private final Long userId = 99L;

    @Test
    void missCallsBackendAndPopulatesCache() {
        CustomerClient client = mock(CustomerClient.class);
        when(client.getPolicyCoverage(7L, userId)).thenReturn(coverage);
        MemBackend backend = new MemBackend();
        CustomerServiceGateway g = gateway(client, backend);

        PolicyCoverageDto result = g.getPolicyCoverage(7L, userId);

        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(client).getPolicyCoverage(7L, userId);
        assertThat(backend.store.keySet()).anyMatch(k -> k.equals("agent:v1:get_policy_coverage:policy:7"));
    }

    @Test
    void hitDoesNotCallBackendAgain() {
        CustomerClient client = mock(CustomerClient.class);
        when(client.getPolicyCoverage(7L, userId)).thenReturn(coverage);
        MemBackend backend = new MemBackend();
        CustomerServiceGateway g = gateway(client, backend);
        g.getPolicyCoverage(7L, userId);
        g.getPolicyCoverage(7L, userId);
        verify(client).getPolicyCoverage(7L, userId); // once total
    }

    @Test
    void doesNotCacheUnavailablePlaceholder() {
        CustomerClient client = mock(CustomerClient.class);
        when(client.getPolicyCoverage(7L, userId)).thenReturn(
                new PolicyCoverageDto(7L, "UNKNOWN", "UNAVAILABLE", "UNKNOWN", "UNKNOWN", null, null, null));
        MemBackend backend = new MemBackend();
        CustomerServiceGateway g = gateway(client, backend);
        g.getPolicyCoverage(7L, userId);
        assertThat(backend.store).doesNotContainKey("agent:v1:get_policy_coverage:policy:7");
    }

    @Test
    void fallbackReturnsNotFoundPlaceholderForFeign404() throws Exception {
        CustomerServiceGateway g = gateway(mock(CustomerClient.class), new MemBackend());
        feign.FeignException ex = feign.FeignException.errorStatus("GET",
                feign.Response.builder().status(404).reason("Not Found")
                        .request(feign.Request.create(feign.Request.HttpMethod.GET,
                                "http://t", java.util.Map.of(), new byte[0], java.nio.charset.StandardCharsets.UTF_8))
                        .build());
        Object result = invokeFallback(g, "policyFallback", 7L, userId, ex);
        assertThat(((PolicyCoverageDto) result).status()).isEqualTo("NOT_FOUND");
    }

    @Test
    void fallbackReturnsUnavailablePlaceholderForGenericError() throws Exception {
        CustomerServiceGateway g = gateway(mock(CustomerClient.class), new MemBackend());
        Object result = invokeFallback(g, "policyFallback", 7L, userId, new RuntimeException("down"));
        assertThat(((PolicyCoverageDto) result).status()).isEqualTo("UNAVAILABLE");
    }

    private static Object invokeFallback(Object target, String method, Object... args) throws Exception {
        java.lang.reflect.Method m = target.getClass().getDeclaredMethod(method,
                Long.class, Long.class, Throwable.class);
        m.setAccessible(true);
        return m.invoke(target, args);
    }
}
