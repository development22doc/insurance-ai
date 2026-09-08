package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.policy_service.entity.IdempotencyRecord;
import com.claimassist.platform.policy_service.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PolicyCreationServiceIdempotencyFingerprintTest {

    IdempotencyRecordRepository repo = mock(IdempotencyRecordRepository.class);
    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
    IdempotencyService service;

    @BeforeEach
    void setUp() {
        org.springframework.transaction.PlatformTransactionManager tm = org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        service = new IdempotencyService(repo, om, tm);
    }

    @Test
    void same_user_same_key_same_fingerprint_returns_cached() {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .key("k1")
                .userId(1L)
                .operation("create-policy")
                .responseBody("{\"policyId\":10,\"policyNumber\":\"POL-1\"}")
                .fingerprint("fprint123")
                .build();
        when(repo.findById("k1")).thenReturn(Optional.of(record));

        Object res = service.execute("k1", "create-policy", 1L, "fprint123", java.util.Map.class, () -> {
            throw new IllegalStateException("Supplier should not be invoked on replay");
        });

        assertThat(res).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) res;
        assertThat(m.get("policyId")).isEqualTo(10);
        assertThat(m.get("policyNumber")).isEqualTo("POL-1");
    }

    @Test
    void same_user_same_key_different_fingerprint_throws_conflict() {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .key("k2")
                .userId(2L)
                .operation("create-policy")
                .responseBody("{\"policyId\":11}")
                .fingerprint("orig-fp")
                .build();
        when(repo.findById("k2")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.execute("k2", "create-policy", 2L, "different-fp", java.util.Map.class, () -> java.util.Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void different_user_same_key_rejected() {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .key("k3")
                .userId(300L)
                .operation("create-policy")
                .responseBody("{}")
                .fingerprint("fp")
                .build();
        when(repo.findById("k3")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.execute("k3", "create-policy", 301L, "fp", java.util.Map.class, () -> java.util.Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missing_or_blank_key_runs_supplier() {
        when(repo.findById(anyString())).thenReturn(Optional.empty());
        Object result = service.execute(null, "create-policy", 1L, (String) null, java.util.Map.class, () -> java.util.Map.of("ok", true));
        assertThat(result).isInstanceOf(java.util.Map.class);
    }
}
