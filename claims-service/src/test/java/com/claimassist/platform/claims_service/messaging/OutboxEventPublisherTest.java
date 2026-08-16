package com.claimassist.platform.claims_service.messaging;

import com.claimassist.platform.claims_service.entity.OutboxEvent;
import com.claimassist.platform.claims_service.repository.OutboxEventRepository;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventPublisherTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final com.claimassist.platform.common_lib.observability.event.EventLogger eventLogger =
            mock(com.claimassist.platform.common_lib.observability.event.EventLogger.class);

    private OutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxEventPublisher(repository, kafkaTemplate, meterRegistry, eventLogger);
        ReflectionTestUtils.setField(publisher, "batchSize", 100);
        ReflectionTestUtils.setField(publisher, "maxAttempts", 3);
        ReflectionTestUtils.setField(publisher, "baseRetryDelayMs", 1000L);
        ReflectionTestUtils.setField(publisher, "maxRetryDelayMs", 60000L);
        ReflectionTestUtils.setField(publisher, "retryMultiplier", 2.0);
        ReflectionTestUtils.setField(publisher, "kafkaDeliveryTimeoutMs", 120000L);
    }

    private OutboxEvent pendingEvent(long id) {
        return OutboxEvent.builder()
                .id(id)
                .aggregateId("agg-" + id)
                .eventType("CLAIM_TEST")
                .topic("claim-update")
                .partitionKey("pk-" + id)
                .payload("{\"id\":" + id + "}")
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .nextAttemptAt(Instant.now())
                .build();
    }

    private CompletableFuture<SendResult<String, String>> successfulFuture() {
        return CompletableFuture.completedFuture(mock(SendResult.class));
    }

    private CompletableFuture<SendResult<String, String>> failedFuture(Exception cause) {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(cause);
        return future;
    }

    @Test
    void publishPendingEvents_Acknowledged_ShouldMarkEventPublishedAndSave() {
        OutboxEvent event = pendingEvent(1L);
        when(repository.findBatchForPublishing(any(), any(), any())).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(successfulFuture());

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
        verify(repository).saveAll(anyList());
    }

    @Test
    void publishPendingEvents_SendFails_BeforeRetryLimit_ShouldKeepPendingAndScheduleRetry() {
        OutboxEvent event = pendingEvent(2L);
        when(repository.findBatchForPublishing(any(), any(), any())).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedFuture(new RuntimeException("boom")));

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isNotNull();
        assertThat(event.getNextAttemptAt()).isAfter(event.getLastAttemptAt());
        assertThat(event.getLastError()).isEqualTo("boom");
        verify(repository).saveAll(anyList());
    }

    @Test
    void publishPendingEvents_SendFails_AtRetryLimit_ShouldMarkFailed() {
        ReflectionTestUtils.setField(publisher, "maxAttempts", 2);
        OutboxEvent event = pendingEvent(3L);
        ReflectionTestUtils.setField(event, "attempts", 1);
        when(repository.findBatchForPublishing(any(), any(), any())).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedFuture(new RuntimeException("boom")));

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getLastError()).isEqualTo("boom");
        verify(repository).saveAll(anyList());
    }

    @Test
    void publishPendingEvents_NoBatch_ShouldDoNothing() {
        when(repository.findBatchForPublishing(any(), any(), any())).thenReturn(List.of());

        publisher.publishPendingEvents();

        verify(repository, org.mockito.Mockito.never()).saveAll(anyList());
    }
}