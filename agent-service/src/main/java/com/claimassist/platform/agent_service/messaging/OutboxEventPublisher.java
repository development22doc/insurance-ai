package com.claimassist.platform.agent_service.messaging;

import com.claimassist.platform.agent_service.entity.OutboxEvent;
import com.claimassist.platform.agent_service.repository.OutboxEventRepository;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> outboxKafkaTemplate;

    @Value("${outbox.publisher.batch-size:100}")
    private int batchSize;

    @Value("${outbox.publisher.max-attempts:5}")
    private int maxAttempts;

    @Value("${outbox.publisher.retry.base-delay-ms:1000}")
    private long baseRetryDelayMs;

    @Value("${outbox.publisher.retry.max-delay-ms:60000}")
    private long maxRetryDelayMs;

    @Value("${outbox.publisher.retry.multiplier:2.0}")
    private double retryMultiplier;

    @Scheduled(
            fixedDelayString = "${outbox.publisher.poll-interval-ms:2000}",
            initialDelayString = "${outbox.publisher.initial-delay-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        Instant now = Instant.now();
        List<OutboxEvent> batch = outboxEventRepository.findBatchForPublishing(
                OutboxStatus.PENDING, now, PageRequest.of(0, batchSize));
        if (batch.isEmpty()) {
            return;
        }

        log.info("Outbox: publishing {} pending event(s)", batch.size());

        for (OutboxEvent event : batch) {
            Instant attemptedAt = Instant.now();
            event.setLastAttemptAt(attemptedAt);
            try {
                outboxKafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload()).get();
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(attemptedAt);
                event.setLastError(null);
                event.setNextAttemptAt(attemptedAt);
            } catch (Exception e) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(resolveErrorMessage(e));
                if (event.getAttempts() >= maxAttempts) {
                    event.setStatus(OutboxStatus.FAILED);
                    event.setNextAttemptAt(attemptedAt);
                    log.error("Outbox event {} exceeded {} attempts, marking FAILED: {}", event.getId(), maxAttempts, event.getLastError());
                } else {
                    event.setNextAttemptAt(calculateNextAttemptAt(attemptedAt, event.getAttempts()));
                    log.warn("Outbox event {} publish attempt {} failed. Next retry at {}: {}",
                            event.getId(), event.getAttempts(), event.getNextAttemptAt(), event.getLastError());
                }
            }
        }
        outboxEventRepository.saveAll(batch);
    }

    private Instant calculateNextAttemptAt(Instant attemptedAt, int attempts) {
        long multiplierExponent = Math.max(0, attempts - 1);
        long computedDelay = (long) (baseRetryDelayMs * Math.pow(retryMultiplier, multiplierExponent));
        long boundedDelay = Math.min(maxRetryDelayMs, Math.max(baseRetryDelayMs, computedDelay));
        return attemptedAt.plusMillis(boundedDelay);
    }

    private String resolveErrorMessage(Exception exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getMessage() != null ? rootCause.getMessage() : rootCause.getClass().getSimpleName();
    }
}
