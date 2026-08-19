package com.claimassist.platform.claims_service.mapper;

import com.claimassist.platform.claims_service.dto.claim.ClaimResponse;
import com.claimassist.platform.claims_service.dto.claim.ClaimSummaryResponse;
import com.claimassist.platform.claims_service.entity.Claim;
import com.claimassist.platform.common_lib.enums.ClaimRole;
import com.claimassist.platform.common_lib.enums.ClaimStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4, Section 19 (DTO mapping): the MapStruct mapper must project the
 * aggregate onto the response DTOs without exposing internal fields and must
 * stringify the status/role enums exactly as the API contract expects.
 */
class ClaimMapperTest {

    private final ClaimMapper mapper = new ClaimMapperImpl();

    @Test
    void toClaimResponseExposesOnlyPublicClaimFields() {
        Claim claim = Claim.builder().id(1L).claimNumber("CLM-1").status(ClaimStatus.SUBMITTED)
                .incidentType("FIRE").build();

        ClaimResponse response = mapper.toClaimResponse(claim);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.claimNumber()).isEqualTo("CLM-1");
        assertThat(response.status()).isEqualTo("SUBMITTED");
        assertThat(response.incidentType()).isEqualTo("FIRE");
    }

    @Test
    void toClaimSummaryResponseMapsStatusAndRoleAsStrings() {
        Claim claim = Claim.builder().id(1L).claimNumber("CLM-1").policyId(5L)
                .incidentType("FIRE").status(ClaimStatus.UNDER_REVIEW)
                .estimatedAmountCents(1000L).approvedAmountCents(900L)
                .incidentDate(Instant.parse("2026-01-01T00:00:00Z"))
                .createdAt(Instant.parse("2026-01-02T00:00:00Z"))
                .build();

        ClaimSummaryResponse response = mapper.toClaimSummaryResponse(claim, ClaimRole.ADJUSTER);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("UNDER_REVIEW");
        assertThat(response.role()).isEqualTo("ADJUSTER");
        assertThat(response.policyId()).isEqualTo(5L);
        assertThat(response.incidentDate()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }
}
