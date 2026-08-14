package com.claimassist.platform.customer_service.service;

import static org.mockito.Mockito.*;


import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.observability.MDCUtility;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.common_lib.observability.event.EventType;
import com.claimassist.platform.customer_service.dto.policy.PolicyResponse;
import com.claimassist.platform.customer_service.entity.CoveragePlan;
import com.claimassist.platform.customer_service.entity.Policy;
import com.claimassist.platform.customer_service.mapper.PolicyMapper;
import com.claimassist.platform.customer_service.repository.CoveragePlanRepository;
import com.claimassist.platform.customer_service.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyQueryServiceTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private CoveragePlanRepository coveragePlanRepository;

    @Mock
    private PolicyMapper policyMapper;

    @InjectMocks
    private PolicyQueryService policyQueryService;

    private Policy testPolicy;
    private CoveragePlan testCoveragePlan;

    @BeforeEach
    void setUp() {
        testCoveragePlan = CoveragePlan.builder()
                .id(1L)
                .name("Plan Type A")
                .productType("HEALTH")
                .deductibleCents(1000L)
                .coverageLimitCents(100000L)
                .build();

        testPolicy = Policy.builder()
                .id(1L)
                .policyNumber("POL-001")
                .status("ACTIVE")
                .coveragePlan(testCoveragePlan)
                .build();
    }

    @Test
    void getMyPolicies_WithExistingPolicies_ShouldReturnPolicyResponses() {
        // Given
        when(policyRepository.findByCustomerId(1L)).thenReturn(List.of(testPolicy));
        when(policyMapper.toPolicyResponse(testPolicy)).thenReturn(new PolicyResponse(
                testPolicy.getId(),
                testPolicy.getPolicyNumber(),
                testPolicy.getStatus(),
                testCoveragePlan.getName(),
                testCoveragePlan.getProductType(),
                testPolicy.getEffectiveDate(),
                testPolicy.getRenewalDate()));

        // When
        List<PolicyResponse> responses = policyQueryService.getMyPolicies(1L);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).policyNumber()).isEqualTo("POL-001");
        verify(policyRepository).findByCustomerId(1L);
        verify(policyMapper).toPolicyResponse(testPolicy);
    }

    @Test
    void getMyPolicies_WithNoPolicies_ShouldReturnEmptyList() {
        // Given
        when(policyRepository.findByCustomerId(1L)).thenReturn(List.of());

        // When
        List<PolicyResponse> responses = policyQueryService.getMyPolicies(1L);

        // Then
        assertThat(responses).isEmpty();
        verify(policyRepository).findByCustomerId(1L);
    }

    @Test
    void getPolicyCoverage_WithValidPolicyAndCallingUser_ShouldReturnCoverageDto() {
        // Given
        when(policyRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(testPolicy));
        when(coveragePlanRepository.findById(1L)).thenReturn(Optional.of(testCoveragePlan));

        // When
        PolicyCoverageDto coverage = policyQueryService.getPolicyCoverage(1L, 1L);

        // Then
        assertThat(coverage).isNotNull();
        assertThat(coverage.policyId()).isEqualTo(1L);
        assertThat(coverage.status()).isEqualTo("ACTIVE");
        assertThat(coverage.productType()).isEqualTo("HEALTH");
        verify(policyRepository).findByIdAndCustomerId(1L, 1L);
        verify(coveragePlanRepository).findById(1L);
    }

    @Test
    void getPolicyCoverage_WithNonExistentPolicy_ShouldThrowResourceNotFoundException() {
        // Given
        when(policyRepository.findByIdAndCustomerId(999L, 1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> policyQueryService.getPolicyCoverage(999L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Policy", String.valueOf(999L));

        verify(policyRepository).findByIdAndCustomerId(999L, 1L);
    }

    @Test
    void getCoveragePlanSnapshot_WithValidId_ShouldReturnSnapshot() {
        // Given
        when(coveragePlanRepository.findById(1L)).thenReturn(Optional.of(testCoveragePlan));

        // When
        PolicyQueryService.CoveragePlanSnapshot snapshot = policyQueryService.getCoveragePlanSnapshot(1L);

        // Then
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.name()).isEqualTo("Plan Type A");
        assertThat(snapshot.productType()).isEqualTo("HEALTH");
        assertThat(snapshot.deductibleCents()).isEqualTo(1000L);
        assertThat(snapshot.coverageLimitCents()).isEqualTo(100000L);
        verify(coveragePlanRepository).findById(1L);
    }

    @Test
    void getCoveragePlanSnapshot_WithNonExistentId_ShouldThrowResourceNotFoundException() {
        // Given
        when(coveragePlanRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> policyQueryService.getCoveragePlanSnapshot(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("CoveragePlan", String.valueOf(999L));

        verify(coveragePlanRepository).findById(999L);
    }

    @Test
    void evictPolicyCoverage_ShouldEvictCache() {
        // When
        policyQueryService.evictPolicyCoverage(1L, 1L);
        // Then - just verify no exception thrown and method executes
    }

    @Test
    void evictMyPolicies_ShouldEvictCache() {
        // When
        policyQueryService.evictMyPolicies(1L);
        // Then - just verify no exception thrown
    }

    @Test
    void evictCoveragePlanSnapshot_ShouldEvictCache() {
        // When
        policyQueryService.evictCoveragePlanSnapshot(1L);
        // Then - just verify no exception thrown
    }
}
