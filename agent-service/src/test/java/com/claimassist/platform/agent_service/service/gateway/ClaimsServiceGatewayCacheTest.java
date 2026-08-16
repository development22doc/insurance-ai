package com.claimassist.platform.agent_service.service.gateway;

import com.claimassist.platform.agent_service.cache.CacheBackend;
import com.claimassist.platform.agent_service.cache.CacheMetrics;
import com.claimassist.platform.agent_service.cache.CacheService;
import com.claimassist.platform.agent_service.client.ClaimsClient;
import com.claimassist.platform.agent_service.config.CacheProperties;
import com.claimassist.platform.common_lib.dto.ClaimDocumentSummaryDto;
import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimsServiceGatewayCacheTest {

    static final class MemBackend implements CacheBackend {
        final Map<String, String> store = new ConcurrentHashMap<>();
        @Override public String get(String k) { return store.get(k); }
        @Override public void set(String k, String v, Duration t) { store.put(k, v); }
        @Override public void delete(String k) { store.remove(k); }
    }

    private ClaimsServiceGateway gateway(ClaimsClient client, MemBackend backend) {
        CacheProperties props = new CacheProperties();
        CacheService cache = new CacheService(backend, new ObjectMapper(), props, new CacheMetrics());
        return new ClaimsServiceGateway(client, cache, props);
    }

    private final ClaimStatusDto status =
            new ClaimStatusDto(99L, 7L, "CLM-99", "UNDER_REVIEW", "FIRE", 1000L, null, List.of());

    @Test
    void missCallsBackendAndPopulatesCache() {
        ClaimsClient client = mock(ClaimsClient.class);
        when(client.getClaimStatus(99L)).thenReturn(status);
        MemBackend backend = new MemBackend();
        ClaimsServiceGateway g = gateway(client, backend);

        ClaimStatusDto result = g.getClaimStatus(99L);

        assertThat(result.status()).isEqualTo("UNDER_REVIEW");
        verify(client).getClaimStatus(99L);
        assertThat(backend.store.keySet())
                .anyMatch(k -> k.equals("agent:v1:get_claim_status:claim:99"));
    }

    @Test
    void hitDoesNotCallBackend() {
        ClaimsClient client = mock(ClaimsClient.class);
        when(client.getClaimStatus(99L)).thenReturn(status);
        MemBackend backend = new MemBackend();
        ClaimsServiceGateway g = gateway(client, backend);
        g.getClaimStatus(99L); // miss → populate

        ClaimStatusDto second = g.getClaimStatus(99L); // hit

        assertThat(second.status()).isEqualTo("UNDER_REVIEW");
        verify(client).getClaimStatus(99L); // still only once (no second backend call)
    }

    @Test
    void doesNotCacheUnavailablePlaceholder() {
        ClaimsClient client = mock(ClaimsClient.class);
        when(client.getClaimStatus(99L)).thenReturn(
                new ClaimStatusDto(99L, null, "UNKNOWN", "UNAVAILABLE", "UNKNOWN", null, null, List.of()));
        MemBackend backend = new MemBackend();
        ClaimsServiceGateway g = gateway(client, backend);
        g.getClaimStatus(99L);
        assertThat(backend.store).doesNotContainKey("agent:v1:get_claim_status:claim:99");
    }

    @Test
    void doesNotCacheNotFoundPlaceholder() {
        ClaimsClient client = mock(ClaimsClient.class);
        when(client.getClaimStatus(99L)).thenReturn(
                new ClaimStatusDto(99L, null, null, "NOT_FOUND", "UNKNOWN", null, null, List.of()));
        MemBackend backend = new MemBackend();
        ClaimsServiceGateway g = gateway(client, backend);
        g.getClaimStatus(99L);
        assertThat(backend.store).doesNotContainKey("agent:v1:get_claim_status:claim:99");
    }

    @Test
    void evictClaimStatusInvalidatesStatusAndDocuments() {
        ClaimsClient client = mock(ClaimsClient.class);
        when(client.getClaimStatus(99L)).thenReturn(status);
        when(client.getClaimDocuments(99L)).thenReturn(List.of());
        MemBackend backend = new MemBackend();
        ClaimsServiceGateway g = gateway(client, backend);
        g.getClaimStatus(99L); // populate status
        backend.store.put("agent:v1:get_claim_documents:claim:99", "[]");

        g.evictClaimStatus(99L);

        assertThat(backend.store).doesNotContainKey("agent:v1:get_claim_status:claim:99");
        assertThat(backend.store).doesNotContainKey("agent:v1:get_claim_documents:claim:99");
    }

    @Test
    void documentsEmptyFromUnavailableFallbackIsNotCached() {
        ClaimsClient client = mock(ClaimsClient.class);
        when(client.getClaimDocuments(99L)).thenReturn(List.of());
        MemBackend backend = new MemBackend();
        ClaimsServiceGateway g = gateway(client, backend);
        List<ClaimDocumentSummaryDto> docs = g.getClaimDocuments(99L);
        assertThat(docs).isEmpty();
        assertThat(backend.store).doesNotContainKey("agent:v1:get_claim_documents:claim:99");
    }

    @Test
    void permissionCheckIsNeverCached() {
        ClaimsClient client = mock(ClaimsClient.class);
        when(client.checkPermission(99L, com.claimassist.platform.common_lib.enums.ClaimPermission.VIEW))
                .thenReturn(true);
        MemBackend backend = new MemBackend();
        ClaimsServiceGateway g = gateway(client, backend);
        g.checkPermission(99L, com.claimassist.platform.common_lib.enums.ClaimPermission.VIEW);
        verify(client).checkPermission(99L, com.claimassist.platform.common_lib.enums.ClaimPermission.VIEW);
        assertThat(backend.store).isEmpty();
    }
}