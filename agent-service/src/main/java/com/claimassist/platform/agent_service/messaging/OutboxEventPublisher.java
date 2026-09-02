package com.claimassist.platform.agent_service.messaging;

import com.claimassist.platform.agent_service.entity.OutboxEvent;
import com.claimassist.platform.agent_service.repository.OutboxEventRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.support.SendResult;

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

    // Await bound for Kafka acknowledgements, derived from the producer's own
    // delivery.timeout.ms (the same property the shared producer factory reads)
    // so the application-level wait never exceeds the producer's delivery timeout.
    @Value("${spring.kafka.producer.properties.delivery.timeout.ms:120000}")
    private long kafkaDeliveryTimeoutMs;

    private record PendingSend(OutboxEvent event, CompletableFuture<SendResult<String, String>> future) {
    }

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

        // Submit all Kafka sends in batch order (createdAt ASC) without blocking on
        // each acknowledgement, so the producer can batch/pipeline them. Ordering per
        // partition is preserved: submission order is unchanged and the idempotent
        // producer guarantees per-partition order for in-flight requests.
        List<PendingSend> pendingSends = new ArrayList<>(batch.size());
        for (OutboxEvent event : batch) {
            Instant attemptedAt = Instant.now();
            event.setLastAttemptAt(attemptedAt);
            // Create ProducerRecord with correlation headers
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    event.getTopic(),
                    event.getPartitionKey(),
                    event.getPayload());

            // Add stored correlation/trace/span/request headers from the OutboxEvent
            // (these were captured at event creation time when the original MDC was available)
            if (event.getCorrelationId() != null) {
                record.headers().add(LoggingConstants.CORRELATION_ID_HEADER, event.getCorrelationId().getBytes());
            }
            if (event.getTraceId() != null) {
                record.headers().add(LoggingConstants.TRACE_ID_HEADER, event.getTraceId().getBytes());
            }
            if (event.getSpanId() != null) {
                record.headers().add(LoggingConstants.SPAN_ID_HEADER, event.getSpanId().getBytes());
            }
            if (event.getRequestId() != null) {
                record.headers().add(LoggingConstants.REQUEST_ID_HEADER, event.getRequestId().getBytes());
            }

            pendingSends.add(new PendingSend(event, outboxKafkaTemplate.send(record)));
        }

        // Await every Kafka acknowledgement with an explicit timeout, and only mark an
        // event PUBLISHED after its ACK. This keeps at-least-once semantics intact:
        // the DB status is never committed as PUBLISHED before Kafka acknowledges, so
        // a send that fails or times out still flows through the retry/FAILED path.
        for (PendingSend pending : pendingSends) {
            OutboxEvent event = pending.event();
            Timer.Sample eventSample = Timer.start(meterRegistry);
            try {
                pending.future().get(kafkaDeliveryTimeoutMs, TimeUnit.MILLISECONDS);
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(event.getLastAttemptAt());
                event.setLastError(null);
                event.setNextAttemptAt(event.getLastAttemptAt());
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
            } catch (ExecutionException | TimeoutException e) {
                handlePublishFailure(event, eventSample, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                handlePublishFailure(event, eventSample, e);
            }
        }
        outboxEventRepository.saveAll(batch);
        sample.stop(Timer.builder("outbox.publisher.batch.duration")
                .tag("size", String.valueOf(batch.size()))
                .register(meterRegistry));
        counter("outbox.batches.processed").increment();
    }

    private void handlePublishFailure(OutboxEvent event, Timer.Sample eventSample, Exception e) {
        event.setAttempts(event.getAttempts() + 1);
        event.setLastError(resolveErrorMessage(e));
        if (event.getAttempts() >= maxAttempts) {
            event.setStatus(OutboxStatus.FAILED);
            event.setNextAttemptAt(event.getLastAttemptAt());
            counter("outbox.events.failed").increment();
            try { eventLogger.logKafkaEvent(null, null, java.util.Map.of(
                    "event", "outbox.event.failed",
                    "outboxId", event.getId(),
                    "error", event.getLastError()
            )); } catch (Exception ignored) {}
            log.error("Outbox event {} exceeded {} attempts, marking FAILED: {}", event.getId(), maxAttempts, event.getLastError());
        } else {
            event.setNextAttemptAt(calculateNextAttemptAt(event.getLastAttemptAt(), event.getAttempts()));
            counter("outbox.events.retry").increment();
            try { eventLogger.logKafkaEvent(null, null, java.util.Map.of(
                    "event", "outbox.event.retry",
                    "outboxId", event.getId(),
                    "attempts", event.getAttempts(),
                    "nextAttemptAt", event.getNextAttemptAt().toString(),
                    "error", event.getLastError()
            )); } catch (Exception ignored) {}
            log.warn("Outbox event {} publish attempt {} failed. Next retry at {}: {}",
                    event.getId(), event.getAttempts(), event.getNextAttemptAt(), event.getLastError());
        }
        eventSample.stop(Timer.builder("outbox.event.publish.duration")
                .tag("status", "failure")
                .register(meterRegistry));
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
