package com.claimassist.platform.claims_service.messaging;

import com.claimassist.platform.claims_service.entity.OutboxEvent;
import com.claimassist.platform.claims_service.repository.OutboxEventRepository;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventProducerTest {

    @Mock
    private OutboxEventRepository repository;

    private OutboxEventProducer producer;

    @BeforeEach
    void setUp() {
        producer = new OutboxEventProducer(repository);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void enqueueCapturesCorrelationContextFromMdcAndPersistsPending() {
        MDC.put(LoggingConstants.MDC_CORRELATION_ID, "corr-1");
        MDC.put(LoggingConstants.MDC_TRACE_ID, "trace-1");

        when(repository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        OutboxEvent event = producer.enqueue("100", "ClaimCreated", "claim-events", "claim-100", "{}");

        assertThat(event.getAggregateId()).isEqualTo("100");
        assertThat(event.getEventType()).isEqualTo("ClaimCreated");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getCorrelationId()).isEqualTo("corr-1");
        assertThat(event.getTraceId()).isEqualTo("trace-1");
        assertThat(event.getPayload()).isEqualTo("{}");
    }

    @Test
    void enqueueIfAbsentSkipsWhenEventAlreadyExists() {
        when(repository.findFirstByAggregateIdAndEventType("100", "ClaimCreated"))
                .thenReturn(Optional.of(new OutboxEvent()));

        boolean created = producer.enqueueIfAbsent("100", "ClaimCreated", "claim-events", "claim-100", "{}");

        assertThat(created).isFalse();
        verify(repository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void enqueueIfAbsentCreatesWhenAbsent() {
        when(repository.findFirstByAggregateIdAndEventType(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(repository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean created = producer.enqueueIfAbsent("100", "ClaimCreated", "claim-events", "claim-100", "{}");

        assertThat(created).isTrue();
        verify(repository).save(any(OutboxEvent.class));
    }

    @Test
    void existsDelegatesToRepository() {
        when(repository.findFirstByAggregateIdAndEventType(eq("100"), eq("ClaimCreated")))
                .thenReturn(Optional.of(new OutboxEvent()));
        assertThat(producer.exists("100", "ClaimCreated")).isTrue();
    }
}