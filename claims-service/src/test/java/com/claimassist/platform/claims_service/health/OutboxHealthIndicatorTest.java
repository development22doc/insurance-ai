package com.claimassist.platform.claims_service.health;

import com.claimassist.platform.claims_service.repository.OutboxEventRepository;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxHealthIndicatorTest {

    private final OutboxEventRepository repo = mock(OutboxEventRepository.class);

    @Test
    void upWhenNoStaleOrExcessiveEvents() {
        when(repo.countStalePendingEvents(any(), any())).thenReturn(0L);
        when(repo.countStaleFailedEvents(any(), any())).thenReturn(0L);
        when(repo.countByStatus(OutboxStatus.PENDING)).thenReturn(5L);
        when(repo.countByStatus(OutboxStatus.FAILED)).thenReturn(1L);

        Health health = new OutboxHealthIndicator(repo).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("totalPendingCount", 5L);
    }

    @Test
    void downWhenStalePendingEventsExist() {
        when(repo.countStalePendingEvents(any(), any())).thenReturn(3L);
        when(repo.countStaleFailedEvents(any(), any())).thenReturn(0L);
        when(repo.countByStatus(OutboxStatus.PENDING)).thenReturn(5L);
        when(repo.countByStatus(OutboxStatus.FAILED)).thenReturn(1L);

        Health health = new OutboxHealthIndicator(repo).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("issue", "Stale events detected in outbox");
    }

    @Test
    void outOfServiceWhenTooManyPending() {
        when(repo.countStalePendingEvents(any(), any())).thenReturn(0L);
        when(repo.countStaleFailedEvents(any(), any())).thenReturn(0L);
        when(repo.countByStatus(OutboxStatus.PENDING)).thenReturn(1001L);
        when(repo.countByStatus(OutboxStatus.FAILED)).thenReturn(1L);

        Health health = new OutboxHealthIndicator(repo).health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("issue", "Too many pending events");
    }

    @Test
    void outOfServiceWhenTooManyFailed() {
        when(repo.countStalePendingEvents(any(), any())).thenReturn(0L);
        when(repo.countStaleFailedEvents(any(), any())).thenReturn(0L);
        when(repo.countByStatus(OutboxStatus.PENDING)).thenReturn(5L);
        when(repo.countByStatus(OutboxStatus.FAILED)).thenReturn(101L);

        Health health = new OutboxHealthIndicator(repo).health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("issue", "Too many failed events");
    }

    @Test
    void downWhenRepositoryThrows() {
        when(repo.countStalePendingEvents(any(), any())).thenThrow(new RuntimeException("db down"));

        Health health = new OutboxHealthIndicator(repo).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }
}