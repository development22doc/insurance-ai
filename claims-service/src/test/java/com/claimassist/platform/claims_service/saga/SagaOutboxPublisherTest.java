package com.claimassist.platform.claims_service.saga;

import com.claimassist.platform.claims_service.entity.OutboxEvent;
import com.claimassist.platform.claims_service.repository.OutboxEventRepository;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SagaOutboxPublisherTest {

    @Mock
    private OutboxEventRepository repository;

    private SagaOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SagaOutboxPublisher(repository);
    }

    @Test
    void enqueuesPendingEventWhenAbsent() {
        when(repository.findFirstByAggregateIdAndEventType("s1", "StepEvent")).thenReturn(Optional.empty());
        when(repository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean created = publisher.enqueueIfAbsent("s1", "StepEvent", "claim-saga-step-command-event", "claim-100", "{}");

        assertThat(created).isTrue();
        verify(repository).save(any(OutboxEvent.class));
    }

    @Test
    void skipsWhenEventAlreadyExists() {
        when(repository.findFirstByAggregateIdAndEventType("s1", "StepEvent"))
                .thenReturn(Optional.of(new OutboxEvent()));

        boolean created = publisher.enqueueIfAbsent("s1", "StepEvent", "t", "k", "{}");

        assertThat(created).isFalse();
        verify(repository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void createdEventCarriesSagaAggregateIdAndPendingStatus() {
        when(repository.findFirstByAggregateIdAndEventType(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        publisher.enqueueIfAbsent("s1", "StepEvent", "claim-saga-step-command-event", "claim-100", "{}");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent captured = captor.getValue();
        assertThat(captured.getAggregateId()).isEqualTo("s1");
        assertThat(captured.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(captured.getEventType()).isEqualTo("StepEvent");
        assertThat(captured.getTopic()).isEqualTo("claim-saga-step-command-event");
        assertThat(captured.getPartitionKey()).isEqualTo("claim-100");
    }
}