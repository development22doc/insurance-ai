package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.policy_service.entity.IdempotencyRecord;
import com.claimassist.platform.policy_service.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

@Component
@Slf4j
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository; // may be null in slice tests
    private final ObjectMapper objectMapper;

    public IdempotencyService(@org.springframework.beans.factory.annotation.Autowired(required = false) IdempotencyRecordRepository idempotencyRecordRepository,
                              ObjectMapper objectMapper) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
    }

    // Backwards-compatible execute signature: delegates to fingerprint-aware execute with null fingerprint
    public <T> T execute(String idempotencyKey, String operation, Long userId, Class<T> responseType, Supplier<T> command) {
        return execute(idempotencyKey, operation, userId, null, responseType, command);
    }

    /**
     * Fingerprint-aware idempotent executor.
     *
     * @param idempotencyKey client-supplied key
     * @param operation operation name
     * @param userId owner id
     * @param fingerprint deterministic request fingerprint (SHA-256 hex) - may be null for backward compat
     */
    public <T> T execute(String idempotencyKey, String operation, Long userId, String fingerprint, Class<T> responseType, Supplier<T> command) {
        // If no idempotency repository is available (slice test contexts), just run command
        if (idempotencyRecordRepository == null) {
            return command.get();
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return command.get();
        }

        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findById(idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            // Ensure same user is replaying the key
            if (record.getUserId() != null && !record.getUserId().equals(userId)) {
                log.warn("Idempotency key {} is owned by another user (owner={} requester={})", idempotencyKey, record.getUserId(), userId);
                throw new IllegalArgumentException("Idempotency key belongs to a different user");
            }

            // If either the stored fingerprint or provided fingerprint is null, fall back to existing behavior (backward compatible)
            if (fingerprint == null || record.getFingerprint() == null) {
                log.info("Idempotent replay for key {} on operation {} (no fingerprint check)", idempotencyKey, operation);
                return readCached(record, responseType);
            }

            // Compare fingerprints - if different, reject as idempotency conflict
            if (!record.getFingerprint().equals(fingerprint)) {
                log.warn("Idempotency key {} replay with conflicting payload (owner={})", idempotencyKey, userId);
                throw new IllegalArgumentException("Idempotency key replay conflict: request payload differs from original");
            }

            log.info("Idempotent replay for key {} on operation {} (fingerprint matched)", idempotencyKey, operation);
            return readCached(record, responseType);
        }

        T result = command.get();

        try {
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .key(idempotencyKey)
                    .userId(userId)
                    .operation(operation)
                    .responseBody(objectMapper.writeValueAsString(result))
                    .fingerprint(fingerprint)
                    .build();
            idempotencyRecordRepository.save(record);
        } catch (DataIntegrityViolationException raceLost) {
            log.warn("Idempotency key {} was concurrently claimed by another request", idempotencyKey);
        } catch (Exception jsonError) {
            log.error("Failed to persist idempotency record for key {}: {}", idempotencyKey, jsonError.getMessage());
        }

        return result;
    }

    private <T> T readCached(IdempotencyRecord record, Class<T> responseType) {
        try {
            return objectMapper.readValue(record.getResponseBody(), responseType);
        } catch (Exception e) {
            log.error("Failed to deserialize cached idempotent response for key {}: {}", record.getKey(), e.getMessage());
            throw new IllegalStateException("Could not read cached idempotent response", e);
        }
    }
}
