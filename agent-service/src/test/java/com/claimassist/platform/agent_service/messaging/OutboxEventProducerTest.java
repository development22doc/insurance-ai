package com.claimassist.platform.agent_service.messaging;

import com.claimassist.platform.agent_service.entity.OutboxEvent;
import com.claimassist.platform.agent_service.repository.OutboxEventRepository;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import org.junit.jupiter.api.AfterEach;
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

    @Mock
    private EventLogger eventLogger;

    private OutboxEventProducer producer() {
        return new OutboxEventProducer(repository, eventLogger);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void enqueueCapturesCorrelationContextFromMdcAndPersistsPending() {
        MDC.put(LoggingConstants.MDC_CORRELATION_ID, "corr-1");
        MDC.put(LoggingConstants.MDC_TRACE_ID, "trace-1");
        MDC.put(LoggingConstants.MDC_SPAN_ID, "span-1");
        when(repository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        OutboxEvent event = producer().enqueue("100", "AgentTurnCompleted", "agent-events", "claim-100", "{}");

        assertThat(event.getAggregateId()).isEqualTo("100");
        assertThat(event.getEventType()).isEqualTo("AgentTurnCompleted");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getCorrelationId()).isEqualTo("corr-1");
        assertThat(event.getTraceId()).isEqualTo("trace-1");
        assertThat(event.getSpanId()).isEqualTo("span-1");
        assertThat(event.getPayload()).isEqualTo("{}");
    }

    @Test
    void enqueueSurvivesEventLoggerFailure() {
        when(repository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("logger down"))
                .when(eventLogger).logKafkaEvent(any(), any(), any());

        OutboxEvent event = producer().enqueue("100", "AgentTurnCompleted", "agent-events", "claim-100", "{}");

        assertThat(event.getAggregateId()).isEqualTo("100");
    }

    @Test
    void enqueueIfAbsentSkipsWhenEventAlreadyExists() {
        when(repository.findFirstByAggregateIdAndEventType("100", "AgentTurnCompleted"))
                .thenReturn(Optional.of(new OutboxEvent()));

        boolean created = producer().enqueueIfAbsent("100", "AgentTurnCompleted", "agent-events", "claim-100", "{}");

        assertThat(created).isFalse();
        verify(repository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void enqueueIfAbsentCreatesWhenAbsent() {
        when(repository.findFirstByAggregateIdAndEventType(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(repository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean created = producer().enqueueIfAbsent("100", "AgentTurnCompleted", "agent-events", "claim-100", "{}");

        assertThat(created).isTrue();
        verify(repository).save(any(OutboxEvent.class));
    }

    @Test
    void existsDelegatesToRepository() {
        when(repository.findFirstByAggregateIdAndEventType(eq("100"), eq("AgentTurnCompleted")))
                .thenReturn(Optional.of(new OutboxEvent()));
        assertThat(producer().exists("100", "AgentTurnCompleted")).isTrue();
    }

    @Test
    void existsReturnsFalseWhenNotFound() {
        when(repository.findFirstByAggregateIdAndEventType(eq("100"), eq("AgentTurnCompleted")))
                .thenReturn(Optional.empty());
        assertThat(producer().exists("100", "AgentTurnCompleted")).isFalse();
    }
}
