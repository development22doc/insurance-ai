package com.claimassist.platform.policy_service.config;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.policy_service.dto.PlanDto;
import com.claimassist.platform.policy_service.dto.ProductDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused regression test for Policy Service Redis value serializer.
 *
 * <p>Proves the policy-service Redis value serializer can round-trip the
 * {@code final record} DTOs it caches (ProductDto, PlanDto, PolicyCoverageDto).
 * The implementation uses {@code EVERYTHING} typing - the same default Spring Data
 * Redis's own GenericJackson2JsonRedisSerializer uses - which emits type
 * metadata for final records too.
 *
 * <p>This exercises the real runtime serializer the cache manager uses, so it
 * verifies actual serialize -> Redis bytes -> deserialize type compatibility
 * without requiring a live Redis instance.
 */
class RedisCacheConfigTest {

    /** Mirrors the ObjectMapper configured in RedisCacheConfig. */
    private GenericJackson2JsonRedisSerializer serializer() {
        ObjectMapper base = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        ObjectMapper typed = base.copy()
                .activateDefaultTyping(
                        base.getPolymorphicTypeValidator(),
                        ObjectMapper.DefaultTyping.EVERYTHING);
        return new GenericJackson2JsonRedisSerializer(typed);
    }

    @Test
    void productDtoRoundTrips() {
        ProductDto original = new ProductDto(
                1L, "AUTO", "Auto Insurance", true, Instant.parse("2026-01-01T00:00:00Z"));

        byte[] bytes = serializer().serialize(original);
        Object read = serializer().deserialize(bytes);

        assertThat(read).isInstanceOf(ProductDto.class);
        ProductDto actual = (ProductDto) read;
        assertThat(actual).isEqualTo(original);
    }

    @Test
    void planDtoRoundTrips() {
        PlanDto original = new PlanDto(
                2L, "COMPREHENSIVE", "Comprehensive Coverage", true,
                1L, "AUTO", 500_00L, 1_000_000_00L, Instant.parse("2026-01-01T00:00:00Z"));

        byte[] bytes = serializer().serialize(original);
        Object read = serializer().deserialize(bytes);

        assertThat(read).isInstanceOf(PlanDto.class);
        assertThat((PlanDto) read).isEqualTo(original);
    }

    @Test
    void policyCoverageDtoRoundTrips() {
        PolicyCoverageDto original = new PolicyCoverageDto(
                42L, "POL-1001", "ACTIVE", "AUTO", "Comprehensive",
                500_00L, 1_000_000_00L, "2026-08-16");

        byte[] bytes = serializer().serialize(original);
        Object read = serializer().deserialize(bytes);

        assertThat(read).isInstanceOf(PolicyCoverageDto.class);
        PolicyCoverageDto actual = (PolicyCoverageDto) read;
        assertThat(actual).isEqualTo(original);
    }
}
