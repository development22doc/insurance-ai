package com.claimassist.platform.claims_service.service.gateway;

import com.claimassist.platform.claims_service.client.PolicyClient;
import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyServiceGatewayTest {

    @Mock
    private PolicyClient policyClient;

    @Mock
    private EventLogger eventLogger;

    private PolicyServiceGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new PolicyServiceGateway(policyClient, eventLogger);
    }

    @Test
    void getPolicyCoverage_callsAuthoritativePolicyContractEndpoint() {
        Long policyId = 123L;
        Long userId = 42L;
        Instant incidentDate = Instant.parse("2025-06-15T00:00:00Z");
        PolicyCoverageDto expectedCoverage = new PolicyCoverageDto(
                999L, "POL-42-999", "Active", "AUTO", "Comprehensive",
                25000L, 1000000L, "2026-01-01T00:00:00Z");

        when(policyClient.getPolicyCoverage(eq(policyId), eq(incidentDate), eq(userId)))
                .thenReturn(expectedCoverage);

        PolicyCoverageDto result = gateway.getPolicyCoverage(policyId, userId, incidentDate);

        assertThat(result).isEqualTo(expectedCoverage);
        verify(policyClient).getPolicyCoverage(eq(policyId), eq(incidentDate), eq(userId));
    }

    @Test
    void getPolicyCoverage_allowsNullIncidentDate_toPassThrough() {
        Long policyId = 123L;
        Long userId = 42L;
        Instant incidentDate = Instant.parse("2025-06-15T00:00:00Z");
        PolicyCoverageDto expectedCoverage = new PolicyCoverageDto(
                999L, "POL-42-999", "Active", "AUTO", "Comprehensive",
                25000L, 1000000L, "2026-01-01T00:00:00Z");

        when(policyClient.getPolicyCoverage(eq(policyId), eq(incidentDate), eq(userId)))
                .thenReturn(expectedCoverage);

        PolicyCoverageDto result = gateway.getPolicyCoverage(policyId, userId, incidentDate);

        assertThat(result).isEqualTo(expectedCoverage);
        verify(policyClient).getPolicyCoverage(eq(policyId), eq(incidentDate), eq(userId));
    }
}
