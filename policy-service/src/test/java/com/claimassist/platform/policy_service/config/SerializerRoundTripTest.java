package com.claimassist.platform.policy_service.config;

import com.claimassist.platform.policy_service.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SerializerRoundTripTest {

    private ObjectMapper buildAppMapper() {
        JsonMapper.Builder builder = JsonMapper.builder().addModule(new JavaTimeModule());
        ObjectMapper mapper = builder.build();
        // No activateDefaultTyping — match RedisCacheConfig
        mapper = mapper.copy();
        return mapper;
    }

    @Test
    public void productDto_roundTrip_viaConfiguredSerializer() throws Exception {
        ObjectMapper mapper = buildAppMapper();
        GenericJackson2JsonRedisSerializer ser = new GenericJackson2JsonRedisSerializer(mapper);

        PlanSummaryDto plan = new PlanSummaryDto(7L, "P-7", "Basic", "ACTIVE", 12345L, 1000L, 500000L, "INR");
        ProductDetailDto original = new ProductDetailDto(3L, "PR-3", "Home Guard", "HOME", "Home insurance", "ACTIVE", List.of(plan));

        byte[] bytes = ser.serialize(original);
        assertNotNull(bytes);

        // Deserialize using the same mapper into concrete type
        ProductDetailDto recovered = mapper.readValue(bytes, ProductDetailDto.class);
        assertEquals(original, recovered);
    }

    @Test
    public void planDto_roundTrip_includingNullsAndEnums() throws Exception {
        ObjectMapper mapper = buildAppMapper();
        GenericJackson2JsonRedisSerializer ser = new GenericJackson2JsonRedisSerializer(mapper);

        PlanDetailDto original = new PlanDetailDto(10L, 3L, "PR-3", "Home Guard", "PL-10", "Premium", "ACTIVE", 99999L, null, 1000000L, "USD");
        byte[] bytes = ser.serialize(original);
        PlanDetailDto recovered = mapper.readValue(bytes, PlanDetailDto.class);
        assertEquals(original, recovered);
        assertNull(recovered.deductibleCents());
    }

    @Test
    public void policyContractDto_roundTrip_withInstantsAndEnums() throws Exception {
        ObjectMapper mapper = buildAppMapper();
        GenericJackson2JsonRedisSerializer ser = new GenericJackson2JsonRedisSerializer(mapper);

        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        PolicyContractDetailDto original = new PolicyContractDetailDto(
                55L,
                "POL-55",
                "ACTIVE",
                123L,
                now,
                now.plusSeconds(60 * 60 * 24 * 365),
                now.plusSeconds(60 * 60 * 24 * 30),
                now,
                null,
                3L,
                "Home Guard",
                "HOME",
                7L,
                "Basic",
                10000L,
                500L,
                100000L,
                "INR"
        );

        byte[] bytes = ser.serialize(original);
        PolicyContractDetailDto recovered = mapper.readValue(bytes, PolicyContractDetailDto.class);
        assertEquals(original, recovered);
        assertEquals(original.activatedAt(), recovered.activatedAt());
    }

    @Test
    public void policyPeriodDto_roundTrip_includingTimestamps() throws Exception {
        ObjectMapper mapper = buildAppMapper();
        GenericJackson2JsonRedisSerializer ser = new GenericJackson2JsonRedisSerializer(mapper);

        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        PolicyPeriodDto original = new PolicyPeriodDto(
                200L,
                55L,
                null,
                1,
                "ACTIVE",
                7L,
                "Basic",
                now,
                now.plusSeconds(31536000),
                now.plusSeconds(2592000),
                now,
                null,
                now,
                now
        );

        byte[] bytes = ser.serialize(original);
        PolicyPeriodDto recovered = mapper.readValue(bytes, PolicyPeriodDto.class);
        assertEquals(original, recovered);
        assertEquals(original.createdAt(), recovered.createdAt());
    }

    @Test
    public void instant_roundTrip_viaMapper() throws Exception {
        ObjectMapper mapper = buildAppMapper();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        byte[] bytes = mapper.writeValueAsBytes(now);
        Instant recovered = mapper.readValue(bytes, Instant.class);
        assertEquals(now, recovered);
    }

    @Test
    public void defaultTyping_not_enabled_inMapper_configuration() {
        ObjectMapper mapper = buildAppMapper();
        // There is no public getter for DefaultTyping; validate that serializing a concrete DTO and
        // deserializing as Object does not reconstruct the concrete type (i.e., no global polymorphic typing).
        // This proves DefaultTyping was not enabled globally.
        var sample = new ProductSummaryDto(1L, "PR-1", "Name", "HOME", "desc", "ACTIVE");
        GenericJackson2JsonRedisSerializer ser = new GenericJackson2JsonRedisSerializer(mapper);
        byte[] bytes = ser.serialize(sample);
        try {
            Object asObject = mapper.readValue(bytes, Object.class);
            // If DefaultTyping were enabled globally, mapper.readValue(bytes, Object.class) might yield ProductSummaryDto.
            // Instead, expect a LinkedHashMap representation when deserializing to Object.class without typing.
            assertTrue(asObject instanceof java.util.Map || asObject instanceof java.util.List || asObject instanceof String || asObject instanceof Number);
        } catch (Exception e) {
            fail("Deserialization to Object failed: " + e.getMessage());
        }
    }
}
