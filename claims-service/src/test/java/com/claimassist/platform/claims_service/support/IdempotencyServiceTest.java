package com.claimassist.platform.claims_service.support;

import com.claimassist.platform.claims_service.dto.claim.ClaimResponse;
import com.claimassist.platform.claims_service.entity.IdempotencyRecord;
import com.claimassist.platform.claims_service.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4, Section 7/17: idempotent command execution. A retried submit must
 * return the ORIGINAL result without re-running the command, and a concurrent
 * claim of the same idempotency key (a unique-constraint race) must not abort
 * the winning request.
 */
class IdempotencyServiceTest {

    private IdempotencyRecordRepository repository;
    private ObjectMapper objectMapper;
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        repository = mock(IdempotencyRecordRepository.class);
        objectMapper = new ObjectMapper();
        service = new IdempotencyService(repository, objectMapper);
    }

    @Test
    void replayReturnsCachedResultWithoutReRunningCommand() {
        ClaimResponse cached = new ClaimResponse(1L, "CLM-1", "SUBMITTED", "FIRE");
        String body = "{\"id\":1,\"claimNumber\":\"CLM-1\",\"status\":\"SUBMITTED\",\"incidentType\":\"FIRE\"}";
        IdempotencyRecord record = IdempotencyRecord.builder()
                .key("k-1").userId(7L).operation("claims.submitClaim").responseBody(body).build();
        when(repository.findById("k-1")).thenReturn(Optional.of(record));

        ClaimResponse result = service.execute("k-1", "claims.submitClaim", 7L,
                ClaimResponse.class, () -> { throw new AssertionError("command must not run on replay"); });

        assertThat(result.id()).isEqualTo(1L);
        verify(repository, never()).save(any());
    }

    @Test
    void runsCommandWhenNoIdempotencyKeyProvided() {
        ClaimResponse produced = new ClaimResponse(1L, "CLM-1", "SUBMITTED", "FIRE");
        ClaimResponse result = service.execute(null, "claims.submitClaim", 7L,
                ClaimResponse.class, () -> produced);
        assertThat(result).isSameAs(produced);
        verify(repository, never()).save(any());
    }

    @Test
    void concurrentKeyRaceDoesNotAbortWinningRequest() {
        ClaimResponse produced = new ClaimResponse(1L, "CLM-1", "SUBMITTED", "FIRE");
        when(repository.findById("k-1")).thenReturn(Optional.empty());
        when(repository.save(any(IdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        ClaimResponse result = service.execute("k-1", "claims.submitClaim", 7L,
                ClaimResponse.class, () -> produced);

        assertThat(result).isSameAs(produced);
    }
}
