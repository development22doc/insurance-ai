package com.claimassist.platform.agent_service.health;

import com.claimassist.platform.agent_service.repository.OutboxEventRepository;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Health indicator for the Transactional Outbox pattern.
 * Monitors the health of outbox event publishing.
 */
@Component("outboxHealth")
@RequiredArgsConstructor
@Slf4j
public class OutboxHealthIndicator implements HealthIndicator {

    private final OutboxEventRepository outboxEventRepository;

    private static final long MAX_PENDING_AGE_SECONDS = 300; // 5 minutes
    private static final long MAX_FAILED_AGE_SECONDS = 900;  // 15 minutes
    private static final int PENDING_THRESHOLD = 1000;
    private static final int FAILED_THRESHOLD = 100;

    @Override
    public Health health() {
        try {
            // Check for stale PENDING events
            long stalePendingCount = outboxEventRepository.countStalePendingEvents(
                    OutboxStatus.PENDING,
                    Instant.now().minus(MAX_PENDING_AGE_SECONDS, ChronoUnit.SECONDS));

            // Check for stale FAILED events
            long staleFailedCount = outboxEventRepository.countStaleFailedEvents(
                    OutboxStatus.FAILED,
                    Instant.now().minus(MAX_FAILED_AGE_SECONDS, ChronoUnit.SECONDS));

            // Count total pending events
            long totalPendingCount = outboxEventRepository.countByStatus(OutboxStatus.PENDING);

            // Count total failed events
            long totalFailedCount = outboxEventRepository.countByStatus(OutboxStatus.FAILED);

            // Build health response
            if (stalePendingCount > 0 || staleFailedCount > 0) {
                return Health.down()
                        .withDetail("stalePendingCount", stalePendingCount)
                        .withDetail("staleFailedCount", staleFailedCount)
                        .withDetail("totalPendingCount", totalPendingCount)
                        .withDetail("totalFailedCount", totalFailedCount)
                        .withDetail("issue", "Stale events detected in outbox")
                        .build();
            }

            if (totalPendingCount > PENDING_THRESHOLD) {
                return Health.outOfService()
                        .withDetail("totalPendingCount", totalPendingCount)
                        .withDetail("threshold", PENDING_THRESHOLD)
                        .withDetail("issue", "Too many pending events")
                        .build();
            }

            if (totalFailedCount > FAILED_THRESHOLD) {
                return Health.outOfService()
                        .withDetail("totalFailedCount", totalFailedCount)
                        .withDetail("threshold", FAILED_THRESHOLD)
                        .withDetail("issue", "Too many failed events")
                        .build();
            }

            return Health.up()
                    .withDetail("totalPendingCount", totalPendingCount)
                    .withDetail("totalFailedCount", totalFailedCount)
                    .withDetail("stalePendingCount", stalePendingCount)
                    .withDetail("staleFailedCount", staleFailedCount)
                    .build();

        } catch (Exception e) {
            log.error("Error checking outbox health", e);
            return Health.down()
                    .withException(e)
                    .build();
        }
    }
}

