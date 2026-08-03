package com.claimassist.platform.claims_service.support;

import com.claimassist.platform.claims_service.entity.IdempotencyRecord;
import com.claimassist.platform.claims_service.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * "Run this command idempotently" helper. See the Lovable-clone platform's
 * identically-named class for full rationale - most important here for
 * POST /claims, where a browser retry must never create a duplicate claim
 * for the same incident.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    public <T> T execute(String idempotencyKey, String operation, Long userId, Class<T> responseType, Supplier<T> command) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return command.get();
        }

        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findById(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent replay for key {} on operation {}", idempotencyKey, operation);
            return readCached(existing.get(), responseType);
        }

        T result = command.get();

        try {
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .key(idempotencyKey)
                    .userId(userId)
                    .operation(operation)
                    .responseBody(objectMapper.writeValueAsString(result))
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
