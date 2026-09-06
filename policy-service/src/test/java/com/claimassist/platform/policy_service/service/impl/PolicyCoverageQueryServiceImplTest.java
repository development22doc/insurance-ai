package com.claimassist.platform.policy_service.service.impl;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.dto.PolicyCoverageProjection;
import com.claimassist.platform.policy_service.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PolicyCoverageQueryServiceImplTest {

    PolicyRepository policyRepository;
    PolicyCoverageQueryServiceImpl service;

    @BeforeEach
    void setup() {
        policyRepository = mock(PolicyRepository.class);
        service = new PolicyCoverageQueryServiceImpl(policyRepository);
    }

    @Test
    void successfulProjectionMapsToDto_and_passesCustomerId() {
        PolicyCoverageProjection proj = mock(PolicyCoverageProjection.class);
        when(proj.getPolicyId()).thenReturn(1L);
        when(proj.getPolicyNumber()).thenReturn("POL-1");
        when(proj.getStatus()).thenReturn("ACTIVE");
        when(proj.getProductType()).thenReturn("AUTO");
        when(proj.getCoveragePlanName()).thenReturn("Basic Plan");
        when(proj.getDeductibleCents()).thenReturn(0L);
        when(proj.getCoverageLimitCents()).thenReturn(100000L);
        when(proj.getRenewalDate()).thenReturn("2027-01-01");

        when(policyRepository.findPolicyCoverageProjectionByPolicyIdAndCustomerId(1L, 77L)).thenReturn(proj);

        PolicyCoverageDto dto = service.getPolicyCoverage(1L, "77");

        assertThat(dto.policyId()).isEqualTo(1L);
        assertThat(dto.policyNumber()).isEqualTo("POL-1");
        assertThat(dto.status()).isEqualTo("ACTIVE");
        assertThat(dto.productType()).isEqualTo("AUTO");
        assertThat(dto.coveragePlanName()).isEqualTo("Basic Plan");
        assertThat(dto.deductibleCents()).isEqualTo(0L);
        assertThat(dto.coverageLimitCents()).isEqualTo(100000L);
        assertThat(dto.renewalDate()).isEqualTo("2027-01-01");

        // verify repository called with converted customer id
        verify(policyRepository).findPolicyCoverageProjectionByPolicyIdAndCustomerId(1L, 77L);
    }

    @Test
    void nullProjection_throwsResourceNotFound() {
        when(policyRepository.findPolicyCoverageProjectionByPolicyIdAndCustomerId(2L, 7L)).thenReturn(null);

        assertThatThrownBy(() -> service.getPolicyCoverage(2L, "7")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void repositoryExceptions_propagate() {
        when(policyRepository.findPolicyCoverageProjectionByPolicyIdAndCustomerId(anyLong(), any())).thenThrow(new RuntimeException("db error"));

        assertThatThrownBy(() -> service.getPolicyCoverage(1L, null)).isInstanceOf(RuntimeException.class).hasMessageContaining("db error");

        // ensure repository called with null customer when header null
        verify(policyRepository).findPolicyCoverageProjectionByPolicyIdAndCustomerId(1L, null);
    }
}