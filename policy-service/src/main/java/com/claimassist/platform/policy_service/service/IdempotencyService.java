package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.policy_service.entity.IdempotencyRecord;
import com.claimassist.platform.policy_service.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.function.Supplier;

@Component
@Slf4j
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository; // may be null in slice tests
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    public IdempotencyService(@org.springframework.beans.factory.annotation.Autowired(required = false) IdempotencyRecordRepository idempotencyRecordRepository,
                              ObjectMapper objectMapper,
                              @org.springframework.beans.factory.annotation.Autowired(required = false) PlatformTransactionManager transactionManager) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
        this.transactionManager = transactionManager;
    }

    // Backwards-compatible execute signature: delegates to fingerprint-aware execute with null fingerprint
    public <T> T execute(String idempotencyKey, String operation, Long userId, Class<T> responseType, Supplier<T> command) {
        return execute(idempotencyKey, operation, userId, null, responseType, command);
    }

    /**
     * Fingerprint-aware idempotent executor.
     *
     * Behavior (backwards-compatible):
     *  - If record exists: validate owner/fingerprint and return cached response.
     *  - If no record: try to atomically claim the key (REQUIRES_NEW). If claim lost, re-check existing.
     *  - On claim success: execute command. After successful execution, finalize the record with the real response.
     *  - If command fails, remove the claim so retries can proceed.
     *
     * @param idempotencyKey client-supplied key
     * @param operation operation name
     * @param userId owner id
     * @param fingerprint deterministic request fingerprint (SHA-256 hex) - may be null for backward compat
     */
    public <T> T execute(String idempotencyKey, String operation, Long userId, String fingerprint, Class<T> responseType, Supplier<T> command) {
        // If no idempotency repository is available (slice test contexts), just run command
        if (idempotencyRecordRepository == null || transactionManager == null) {
            return command.get();
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return command.get();
        }

        // 1) Fast check for existing completed record
        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findById(idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();

            // Reject if owner differs
            if (record.getUserId() != null && !record.getUserId().equals(userId)) {
                log.warn("Idempotency key {} is owned by another user (owner={} requester={})", idempotencyKey, record.getUserId(), userId);
                throw new IllegalArgumentException("Idempotency key belongs to a different user");
            }

            // Detect in-progress marker
            if (isInProgress(record)) {
                log.info("Idempotency key {} is currently IN_PROGRESS", idempotencyKey);
                            // Map IN_PROGRESS to a conflict so callers receive 409 via GlobalExceptionHandler
                            throw new DataIntegrityViolationException("Idempotency key is currently in progress: " + idempotencyKey);
                        }

            // Fingerprint handling (backwards compatible)
            if (fingerprint == null || record.getFingerprint() == null) {
                log.info("Idempotent replay for key {} on operation {} (no fingerprint check)", idempotencyKey, operation);
                return readCached(record, responseType);
            }
            if (!record.getFingerprint().equals(fingerprint)) {
                log.warn("Idempotency key {} replay with conflicting payload (owner={})", idempotencyKey, userId);
                throw new IllegalArgumentException("Idempotency key replay conflict: request payload differs from original");
            }

            log.info("Idempotent replay for key {} on operation {} (fingerprint matched)", idempotencyKey, operation);
            return readCached(record, responseType);
        }

        // 2) Attempt to claim the key in a short-lived new transaction so other concurrent requests can observe it
        try {
            claimKey(idempotencyKey, operation, userId, fingerprint);
        } catch (DataIntegrityViolationException alreadyClaimed) {
            // Another request claimed concurrently - re-load and apply existing semantics
            Optional<IdempotencyRecord> nowExisting = idempotencyRecordRepository.findById(idempotencyKey);
            if (nowExisting.isPresent()) {
                IdempotencyRecord record = nowExisting.get();
                if (record.getUserId() != null && !record.getUserId().equals(userId)) {
                    log.warn("Idempotency key {} is owned by another user (owner={} requester={})", idempotencyKey, record.getUserId(), userId);
                    throw new IllegalArgumentException("Idempotency key belongs to a different user");
                }
                if (isInProgress(record)) {
                                    // Map IN_PROGRESS to DataIntegrityViolation so it becomes a 409 CONFLICT via the global handler
                                    throw new DataIntegrityViolationException("Idempotency key is currently in progress: " + idempotencyKey);
                                }
                if (fingerprint == null || record.getFingerprint() == null) return readCached(record, responseType);
                if (!record.getFingerprint().equals(fingerprint)) throw new IllegalArgumentException("Idempotency key replay conflict: request payload differs from original");
                return readCached(record, responseType);
            }
            // If not present after race (extremely unlikely), fall through and try command
        }

        // 3) Execute the guarded command (may perform DB work / external calls)
        T result;
        try {
            result = command.get();
        } catch (RuntimeException | Error ex) {
            // Remove the claim so retries can attempt again
            try {
                removeClaim(idempotencyKey);
            } catch (Exception cleanupEx) {
                log.warn("Failed to remove idempotency claim for key {} after command failure: {}", idempotencyKey, cleanupEx.getMessage());
            }
            throw ex;
        }

        // 4) Finalize: persist the real responseBody after the outer transaction commits.
        final String finalResponseJson;
        try {
            finalResponseJson = objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Failed to serialize idempotent response for key {}: {}", idempotencyKey, e.getMessage());
            // Best effort: remove claim to avoid poisoning
            try { removeClaim(idempotencyKey); } catch (Exception ignore) {}
            return result;
        }

        // If a transaction is active, schedule finalize after commit; otherwise finalize immediately
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        finalizeRecord(idempotencyKey, finalResponseJson);
                    } catch (Exception ex) {
                                        log.error("Failed to finalize idempotency record for key {}: {} - removing IN_PROGRESS claim", idempotencyKey, ex.getMessage());
                                        try {
                                            removeClaim(idempotencyKey);
                                        } catch (Exception remEx) {
                                            log.warn("Failed to remove idempotency claim for key {} after finalize failure: {}", idempotencyKey, remEx.getMessage());
                                        }
                                    }
                                }

                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        try {
                            removeClaim(idempotencyKey);
                        } catch (Exception ex) {
                            log.warn("Failed to remove idempotency claim for key {} after rollback: {}", idempotencyKey, ex.getMessage());
                        }
                    }
                }
            });
        } else {
            try {
                finalizeRecord(idempotencyKey, finalResponseJson);
            } catch (Exception ex) {
                        log.error("Failed to finalize idempotency record for key {}: {} - removing IN_PROGRESS claim", idempotencyKey, ex.getMessage());
                        try { removeClaim(idempotencyKey); } catch (Exception remEx) { log.warn("Failed to remove idempotency claim for key {} after finalize failure: {}", idempotencyKey, remEx.getMessage()); }
                    }
                }

        return result;
    }

    // Helper: mark key as claimed (IN_PROGRESS) in a new transaction
    private void claimKey(String idempotencyKey, String operation, Long userId, String fingerprint) {
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tt.execute(status -> {
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .key(idempotencyKey)
                    .userId(userId)
                    .operation(operation)
                    .responseBody("{\"__idempotency_status\":\"IN_PROGRESS\"}")
                    .fingerprint(fingerprint)
                    .build();
            idempotencyRecordRepository.save(record);
            return null;
        });
    }

    // Helper: finalize the record with the real response JSON (new transaction)
    private void finalizeRecord(String idempotencyKey, String responseBodyJson) {
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tt.execute(status -> {
            Optional<IdempotencyRecord> r = idempotencyRecordRepository.findById(idempotencyKey);
            if (r.isPresent()) {
                IdempotencyRecord rec = r.get();
                rec.setResponseBody(responseBodyJson);
                idempotencyRecordRepository.save(rec);
            } else {
                // Insert if missing (best-effort)
                IdempotencyRecord rec = IdempotencyRecord.builder()
                        .key(idempotencyKey)
                        .userId(null)
                        .operation(null)
                        .responseBody(responseBodyJson)
                        .build();
                idempotencyRecordRepository.save(rec);
            }
            return null;
        });
    }

    // Helper: remove a claim (new transaction)
    private void removeClaim(String idempotencyKey) {
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tt.execute(status -> {
            idempotencyRecordRepository.deleteById(idempotencyKey);
            return null;
        });
    }

    private boolean isInProgress(IdempotencyRecord record) {
        try {
            if (record.getResponseBody() == null) return false;
            JsonNode node = objectMapper.readTree(record.getResponseBody());
            if (node.has("__idempotency_status") && "IN_PROGRESS".equalsIgnoreCase(node.get("__idempotency_status").asText())) {
                return true;
            }
        } catch (Exception e) {
            // ignore - treat as not in progress
        }
        return false;
    }

    private <T> T readCached(IdempotencyRecord record, Class<T> responseType) {
        try {
            return objectMapper.readValue(record.getResponseBody(), responseType);
        } catch (Exception e) {
            log.error("Failed to deserialize cached idempotent response for key {}: {}", record.getKey(), e.getMessage());
            throw new IllegalStateException("Could not read cached idempotent response", e);
        }
    }

    /**
     * Retrieve cached response as a raw Map for accessing individual fields.
     * Used when the caller needs specific fields from a cached response.
     */
    public java.util.Map<String, Object> getCachedResponse(String idempotencyKey, Long userId, String operation) {
        if (idempotencyRecordRepository == null) {
            return null;
        }

        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findById(idempotencyKey);
        if (existing.isEmpty()) {
            return null;
        }

        IdempotencyRecord record = existing.get();

        // Validate owner
        if (record.getUserId() != null && !record.getUserId().equals(userId)) {
            log.warn("Idempotency key {} is owned by another user (owner={} requester={})", idempotencyKey, record.getUserId(), userId);
            throw new IllegalArgumentException("Idempotency key belongs to a different user");
        }

        // Validate operation matches
        if (operation != null && !operation.equals(record.getOperation())) {
            log.warn("Idempotency key {} operation mismatch (expected={}, actual={})", idempotencyKey, operation, record.getOperation());
            throw new IllegalArgumentException("Idempotency key operation mismatch");
        }

        // Skip IN_PROGRESS records
        if (isInProgress(record)) {
            return null;
        }

        try {
            return objectMapper.readValue(record.getResponseBody(), new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize cached response as Map for key {}: {}", idempotencyKey, e.getMessage());
            return null;
        }
    }
}
