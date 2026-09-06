package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.policy_service.entity.IdempotencyRecord;
import com.claimassist.platform.policy_service.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PolicyCreationServiceIdempotencyRulesTest {

    IdempotencyRecordRepository repo = mock(IdempotencyRecordRepository.class);
    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
    IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repo, om);
    }

    @Test
    void replay_by_different_user_throws() {
        IdempotencyRecord record = IdempotencyRecord.builder().key("k1").userId(100L).operation("create-policy").responseBody("{}")
                .build();
        when(repo.findById("k1")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.execute("k1", "create-policy", 200L, java.util.Map.class, () -> java.util.Map.of()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repo, times(1)).findById("k1");
    }

    @Test
    void missing_key_allowed_in_service_but_policy_service_rejects() {
        // If repo is present, service will run supplier when key null
        when(repo.findById(anyString())).thenReturn(Optional.empty());
        // supplier should be invoked
        Object result = service.execute(null, "create-policy", 1L, java.util.Map.class, () -> java.util.Map.of("ok", true));
        assert result instanceof java.util.Map;
    }
}
