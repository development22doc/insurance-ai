package com.claimassist.platform.customer_service.mapper;

import com.claimassist.platform.customer_service.dto.policy.PolicyResponse;
import com.claimassist.platform.customer_service.entity.CoveragePlan;
import com.claimassist.platform.customer_service.entity.Policy;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyMapperTest {

    @Test
    void toPolicyResponse_mapsAllFields() {
        CoveragePlan plan = CoveragePlan.builder()
                .id(2L).name("Comprehensive").productType("AUTO").build();
        Policy policy = Policy.builder()
                .id(10L)
                .coveragePlan(plan)
                .policyNumber("POL-1")
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2026-01-01T00:00:00Z"))
                .renewalDate(Instant.parse("2027-01-01T00:00:00Z"))
                .build();

        PolicyResponse response = new PolicyMapperImpl().toPolicyResponse(policy);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.policyNumber()).isEqualTo("POL-1");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.coveragePlanName()).isEqualTo("Comprehensive");
        assertThat(response.productType()).isEqualTo("AUTO");
        assertThat(response.effectiveDate()).isEqualTo(policy.getEffectiveDate());
        assertThat(response.renewalDate()).isEqualTo(policy.getRenewalDate());
    }

    @Test
    void toPolicyResponse_nullRenewalDate_remainsNull() {
        CoveragePlan plan = CoveragePlan.builder().id(2L).name("Basic").productType("HOME").build();
        Policy policy = Policy.builder()
                .id(3L)
                .coveragePlan(plan)
                .policyNumber("POL-2")
                .status("PENDING")
                .effectiveDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        PolicyResponse response = new PolicyMapperImpl().toPolicyResponse(policy);

        assertThat(response.renewalDate()).isNull();
        assertThat(response.productType()).isEqualTo("HOME");
    }
}