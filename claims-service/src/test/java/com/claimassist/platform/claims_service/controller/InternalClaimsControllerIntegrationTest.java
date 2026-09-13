package com.claimassist.platform.claims_service.controller;

import com.claimassist.platform.common_lib.dto.ClaimStatusDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test to verify ClaimStatusDto can be properly serialized/deserialized
 * by Jackson, ensuring Feign clients can consume it without ClassCastException.
 */
class InternalClaimsControllerIntegrationTest {

    @Test
    void claimStatusDtoSerializationDeserializationWorks() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        ClaimStatusDto originalDto = new ClaimStatusDto(
                1L,
                5L,
                "CLM-001",
                "SUBMITTED",
                "FIRE",
                100000L,
                null,
                List.of(new ClaimStatusDto.StatusHistoryEntry(
                        "NONE",
                        "SUBMITTED",
                        "1",
                        "2025-01-01T00:00:00Z"
                ))
        );

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(originalDto);
        assertThat(json).contains("\"claimId\":1");
        assertThat(json).contains("\"status\":\"SUBMITTED\"");

        // Deserialize back to ClaimStatusDto
        ClaimStatusDto deserializedDto = objectMapper.readValue(json, ClaimStatusDto.class);

        // Verify the deserialized object is the correct type, not LinkedHashMap
        assertThat(deserializedDto).isInstanceOf(ClaimStatusDto.class);
        assertThat(deserializedDto.claimId()).isEqualTo(1L);
        assertThat(deserializedDto.status()).isEqualTo("SUBMITTED");
        assertThat(deserializedDto.history()).hasSize(1);
        assertThat(deserializedDto.history().get(0).fromStatus()).isEqualTo("NONE");
    }
}
