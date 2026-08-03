package com.claimassist.platform.claims_service.saga;

import com.claimassist.platform.claims_service.entity.ProcessedEvent;
import com.claimassist.platform.claims_service.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Saga step idempotency manager - ensures that saga steps are only
 * processed once even if messages are delivered multiple times.
 *
 * Kafka's at-least-once delivery guarantee makes this essential for
 * preventing double-processing of saga steps.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaIdempotencyManager {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * Check if a saga step message has already been processed.
     * Uses sagaId + step as the idempotency key.
     *
     * @param sagaId The saga ID
     * @param step The saga step name
     * @return true if already processed, false otherwise
     */
    public boolean isAlreadyProcessed(String sagaId, String step) {
        String idempotencyKey = generateKey(sagaId, step);
        boolean exists = processedEventRepository.existsById(idempotencyKey);

        if (exists) {
            log.debug("Saga step already processed (idempotent dedup): sagaId={}, step={}", sagaId, step);
        }

        return exists;
    }

    /**
     * Mark a saga step as processed to prevent re-processing on retry.
     * Should be called after successful processing.
     *
     * @param sagaId The saga ID
     * @param step The saga step name
     */
    public void markAsProcessed(String sagaId, String step) {
        String idempotencyKey = generateKey(sagaId, step);

        if (!processedEventRepository.existsById(idempotencyKey)) {
            processedEventRepository.save(new ProcessedEvent(idempotencyKey, LocalDateTime.now()));
            log.debug("Marked saga step as processed: sagaId={}, step={}", sagaId, step);
        }
    }

    /**
     * Check and mark in one atomic operation (if repository supports it).
     *
     * @param sagaId The saga ID
     * @param step The saga step name
     * @return true if this is the first time processing (marked now), false if already processed
     */
    public boolean checkAndMark(String sagaId, String step) {
        if (isAlreadyProcessed(sagaId, step)) {
            return false;
        }

        markAsProcessed(sagaId, step);
        return true;
    }

    /**
     * Generate idempotency key from saga ID and step.
     */
    private static String generateKey(String sagaId, String step) {
        return sagaId + ":" + step;
    }
}

