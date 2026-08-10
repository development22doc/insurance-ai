package com.claimassist.platform.claims_service.messaging;

import com.claimassist.platform.claims_service.entity.OutboxEvent;
import com.claimassist.platform.claims_service.repository.OutboxEventRepository;
import com.claimassist.platform.common_lib.enums.OutboxStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> outboxKafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final com.claimassist.platform.common_lib.observability.event.EventLogger eventLogger;

    private final ConcurrentMap<String, Timer> statusTimers = new ConcurrentHashMap<>();

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
        Timer.Sample sample = Timer.start(meterRegistry);
        Instant now = Instant.now();
        List<OutboxEvent> batch = outboxEventRepository.findBatchForPublishing(
                OutboxStatus.PENDING, now, PageRequest.of(0, batchSize));

        if (batch.isEmpty()) {
            return;
        }

        log.info("Outbox: publishing {} pending event(s)", batch.size());

        for (OutboxEvent event : batch) {
            Timer.Sample eventSample = Timer.start(meterRegistry);
            Instant attemptedAt = Instant.now();
            event.setLastAttemptAt(attemptedAt);
            try {
                // Create ProducerRecord with correlation headers
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        event.getTopic(),
                        event.getPartitionKey(),
                        event.getPayload());

                // Add correlation/trace/span headers from MDC so downstream consumers can correlate
                String correlationId = MDC.get(LoggingConstants.MDC_CORRELATION_ID);
                if (correlationId != null) {
                    record.headers().add(LoggingConstants.CORRELATION_ID_HEADER, correlationId.getBytes());
                }
                String traceId = MDC.get(LoggingConstants.MDC_TRACE_ID);
                if (traceId != null) {
                    record.headers().add(LoggingConstants.TRACE_ID_HEADER, traceId.getBytes());
                }
                String spanId = MDC.get(LoggingConstants.MDC_SPAN_ID);
                if (spanId != null) {
                    record.headers().add(LoggingConstants.SPAN_ID_HEADER, spanId.getBytes());
                }

                outboxKafkaTemplate.send(record).get();
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(attemptedAt);
                event.setLastError(null);
                event.setNextAttemptAt(attemptedAt);
                counter("outbox.events.published").increment();
                eventSample.stop(Timer.builder("outbox.event.publish.duration")
                        .tag("status", "success")
                        .register(meterRegistry));
                try {
                    eventLogger.logKafkaEvent(null, null, java.util.Map.of(
                            "event", "outbox.event.published",
                            "outboxId", event.getId(),
                            "topic", event.getTopic(),
                            "aggregateId", event.getAggregateId()
                    ));
                } catch (Exception ignored) {}
                log.debug("Outbox event {} published successfully", event.getId());
            } catch (Exception e) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(resolveErrorMessage(e));
                if (event.getAttempts() >= maxAttempts) {
                    event.setStatus(OutboxStatus.FAILED);
                    event.setNextAttemptAt(attemptedAt);
                    counter("outbox.events.failed").increment();
                    try {
                        eventLogger.logKafkaEvent(null, null, java.util.Map.of(
                                "event", "outbox.event.failed",
                                "outboxId", event.getId(),
                                "error", event.getLastError()
                        ));
                    } catch (Exception ignored) {}
                    log.error("Outbox event {} exceeded {} attempts, marking FAILED: {}", event.getId(), maxAttempts, event.getLastError());
                } else {
                    event.setNextAttemptAt(calculateNextAttemptAt(attemptedAt, event.getAttempts()));
                    counter("outbox.events.retry").increment();
                    try {
                        eventLogger.logKafkaEvent(null, null, java.util.Map.of(
                                "event", "outbox.event.retry",
                                "outboxId", event.getId(),
                                "attempts", event.getAttempts(),
                                "nextAttemptAt", event.getNextAttemptAt().toString(),
                                "error", event.getLastError()
                        ));
                    } catch (Exception ignored) {}
                    log.warn("Outbox event {} publish attempt {} failed. Next retry at {}: {}",
                            event.getId(), event.getAttempts(), event.getNextAttemptAt(), event.getLastError());
                }
                eventSample.stop(Timer.builder("outbox.event.publish.duration")
                        .tag("status", "failure")
                        .register(meterRegistry));
            }
        }

        outboxEventRepository.saveAll(batch);
        sample.stop(Timer.builder("outbox.publisher.batch.duration")
                .tag("size", String.valueOf(batch.size()))
                .register(meterRegistry));
        counter("outbox.batches.processed").increment();
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

    private Counter counter(String name) {
        return Counter.builder(name).register(meterRegistry);
    }
}
