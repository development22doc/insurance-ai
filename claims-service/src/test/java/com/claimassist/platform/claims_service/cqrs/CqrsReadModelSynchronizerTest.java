package com.claimassist.platform.claims_service.cqrs;

import com.claimassist.platform.claims_service.cache.CacheService;
import com.claimassist.platform.common_lib.event.ClaimUpdateResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CqrsReadModelSynchronizerTest {

    private final CacheService cacheService = mock(CacheService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CqrsReadModelSynchronizer synchronizer = new CqrsReadModelSynchronizer(null, cacheService, objectMapper);

    private String eventJson(ClaimUpdateResponseEvent event) throws Exception {
        return objectMapper.writeValueAsString(event);
    }

    @Test
    void successfulUpdateInvalidatesClaimCacheAndAcks() throws Exception {
        Acknowledgment ack = mock(Acknowledgment.class);
        String raw = eventJson(new ClaimUpdateResponseEvent("saga-1", 42L, true, null));

        synchronizer.synchronizeClaimReadModelOnUpdate(raw, ack);

        verify(cacheService).invalidateClaimCache(42L);
        verify(ack).acknowledge();
    }

    @Test
    void failedUpdateSkipsCacheButStillAcks() throws Exception {
        Acknowledgment ack = mock(Acknowledgment.class);
        String raw = eventJson(new ClaimUpdateResponseEvent("saga-2", 42L, false, "rejected"));

        synchronizer.synchronizeClaimReadModelOnUpdate(raw, ack);

        verify(cacheService, never()).invalidateClaimCache(42L);
        verify(ack).acknowledge();
    }

    @Test
    void nullClaimIdSkipsCacheButStillAcks() throws Exception {
        Acknowledgment ack = mock(Acknowledgment.class);
        String raw = eventJson(new ClaimUpdateResponseEvent("saga-3", null, true, null));

        synchronizer.synchronizeClaimReadModelOnUpdate(raw, ack);

        verify(cacheService, never()).invalidateClaimCache(org.mockito.ArgumentMatchers.anyLong());
        verify(ack).acknowledge();
    }

    @Test
    void malformedMessageThrowsAndDoesNotAck() {
        Acknowledgment ack = mock(Acknowledgment.class);

        assertThatThrownBy(() -> synchronizer.synchronizeClaimReadModelOnUpdate("not-json", ack))
                .isInstanceOf(Exception.class);
        verify(ack, never()).acknowledge();
    }

    @Test
    void sagaCompletionInvalidatesClaimCacheAndAcks() throws Exception {
        Acknowledgment ack = mock(Acknowledgment.class);
        String raw = "{\"sagaId\":\"s\",\"claimId\":7,\"status\":\"COMPLETED\",\"detail\":\"ok\"}";

        synchronizer.synchronizeClaimReadModelOnSagaCompletion(raw, ack);

        verify(cacheService).invalidateClaimCache(7L);
        verify(ack).acknowledge();
    }

    @Test
    void sagaCompletionWithoutClaimIdStillAcks() throws Exception {
        Acknowledgment ack = mock(Acknowledgment.class);
        String raw = "{\"sagaId\":\"s\",\"claimId\":null,\"status\":\"FAILED\",\"detail\":\"x\"}";

        synchronizer.synchronizeClaimReadModelOnSagaCompletion(raw, ack);

        verify(cacheService, never()).invalidateClaimCache(org.mockito.ArgumentMatchers.anyLong());
        verify(ack).acknowledge();
    }
}