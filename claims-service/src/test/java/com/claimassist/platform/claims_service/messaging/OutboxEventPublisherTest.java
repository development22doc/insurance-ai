package com.claimassist.platform.claims_service.messaging;

import com.claimassist.platform.claims_service.entity.OutboxEvent;
import com.claimassist.platform.claims_service.repository.OutboxEventRepository;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private EventLogger eventLogger;

    private OutboxEventPublisher publisher;

    private OutboxEventPublisher newPublisher() {
        OutboxEventPublisher p = new OutboxEventPublisher(repository, kafkaTemplate, new SimpleMeterRegistry(), eventLogger);
        ReflectionTestUtils.setField(p, "batchSize", 100);
        ReflectionTestUtils.setField(p, "maxAttempts", 5);
        ReflectionTestUtils.setField(p, "baseRetryDelayMs", 1000L);
        ReflectionTestUtils.setField(p, "maxRetryDelayMs", 60000L);
        ReflectionTestUtils.setField(p, "retryMultiplier", 2.0);
        ReflectionTestUtils.setField(p, "kafkaDeliveryTimeoutMs", 1000L);
        return p;
    }

    private OutboxEvent pendingEvent(long id) {
        return OutboxEvent.builder()
                .id(id)
                .aggregateId("100")
                .eventType("ClaimCreated")
                .topic("claim-events")
                .partitionKey("claim-100")
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .correlationId("corr-1")
                .createdAt(Instant.now().minusSeconds(5))
                .nextAttemptAt(Instant.now().minusSeconds(5))
                .build();
    }

    @Test
    void marksEventPublishedOnlyAfterKafkaAckAndPropagatesCorrelationHeader() {
        publisher = newPublisher();
        OutboxEvent event = pendingEvent(1L);
        when(repository.findBatchForPublishing(eq(OutboxStatus.PENDING), any(Instant.class), any(PageRequest.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(org.mockito.Mockito.mock(SendResult.class)));

        publisher.publishPendingEvents();

        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, String> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("claim-events");
        assertThat(record.key()).isEqualTo("claim-100");
        assertThat(record.headers().lastHeader(LoggingConstants.CORRELATION_ID_HEADER)).isNotNull();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
        verify(repository).saveAll(List.of(event));
    }

    @Test
    void transientFailureKeepsEventPendingAndSchedulesBackoffRetry() {
        publisher = newPublisher();
        OutboxEvent event = pendingEvent(1L);
        when(repository.findBatchForPublishing(eq(OutboxStatus.PENDING), any(Instant.class), any(PageRequest.class)))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unreachable"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("broker unreachable");
        assertThat(event.getNextAttemptAt()).isAfter(Instant.now());
        verify(repository).saveAll(List.of(event));
    }

    @Test
    void exhaustedRetriesMarkEventFailed() {
        publisher = newPublisher();
        ReflectionTestUtils.setField(publisher, "maxAttempts", 1);
        OutboxEvent event = pendingEvent(1L);
        when(repository.findBatchForPublishing(eq(OutboxStatus.PENDING), any(Instant.class), any(PageRequest.class)))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unreachable"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(1);
        verify(repository).saveAll(List.of(event));
    }

    @Test
    void doesNothingWhenNoPendingBatch() {
        publisher = newPublisher();
        when(repository.findBatchForPublishing(eq(OutboxStatus.PENDING), any(Instant.class), any(PageRequest.class)))
                .thenReturn(List.of());
        publisher.publishPendingEvents();
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        verify(repository, never()).saveAll(any());
    }
}