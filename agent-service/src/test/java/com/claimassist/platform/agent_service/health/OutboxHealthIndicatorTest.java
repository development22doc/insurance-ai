package com.claimassist.platform.agent_service.health;

import com.claimassist.platform.agent_service.repository.OutboxEventRepository;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxHealthIndicatorTest {

    @Mock
    private OutboxEventRepository repository;

    @Test
    void upWhenNoStaleEventsAndWithinThresholds() {
        when(repository.countStalePendingEvents(any(OutboxStatus.class), any(Instant.class))).thenReturn(0L);
        when(repository.countStaleFailedEvents(any(OutboxStatus.class), any(Instant.class))).thenReturn(0L);
        when(repository.countByStatus(OutboxStatus.PENDING)).thenReturn(10L);
        when(repository.countByStatus(OutboxStatus.FAILED)).thenReturn(0L);

        Health health = new OutboxHealthIndicator(repository).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("totalPendingCount", 10L);
    }

    @Test
    void downWhenStalePendingOrFailedEvents() {
        when(repository.countStalePendingEvents(any(OutboxStatus.class), any(Instant.class))).thenReturn(5L);
        when(repository.countStaleFailedEvents(any(OutboxStatus.class), any(Instant.class))).thenReturn(0L);

        Health health = new OutboxHealthIndicator(repository).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("stalePendingCount", 5L);
    }

    @Test
    void outOfServiceWhenTooManyPendingEvents() {
        when(repository.countStalePendingEvents(any(OutboxStatus.class), any(Instant.class))).thenReturn(0L);
        when(repository.countStaleFailedEvents(any(OutboxStatus.class), any(Instant.class))).thenReturn(0L);
        when(repository.countByStatus(OutboxStatus.PENDING)).thenReturn(2000L);
        when(repository.countByStatus(OutboxStatus.FAILED)).thenReturn(0L);

        Health health = new OutboxHealthIndicator(repository).health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
    }

    @Test
    void outOfServiceWhenTooManyFailedEvents() {
        when(repository.countStalePendingEvents(any(OutboxStatus.class), any(Instant.class))).thenReturn(0L);
        when(repository.countStaleFailedEvents(any(OutboxStatus.class), any(Instant.class))).thenReturn(0L);
        when(repository.countByStatus(OutboxStatus.PENDING)).thenReturn(0L);
        when(repository.countByStatus(OutboxStatus.FAILED)).thenReturn(500L);

        Health health = new OutboxHealthIndicator(repository).health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
    }

    @Test
    void downWhenRepositoryFails() {
        when(repository.countStalePendingEvents(any(OutboxStatus.class), any(Instant.class)))
                .thenThrow(new IllegalStateException("db down"));

        Health health = new OutboxHealthIndicator(repository).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
