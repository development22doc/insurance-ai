package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.customer_service.client.PolicyServiceAdapter;
import com.claimassist.platform.customer_service.dto.policy.CancelRequestDto;
import com.claimassist.platform.customer_service.dto.policy.ReinstateRequestDto;
import com.claimassist.platform.customer_service.entity.Policy;
import com.claimassist.platform.customer_service.mapper.PolicyMapper;
import com.claimassist.platform.customer_service.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PolicyCancelReinstateDelegationTest {

    private PolicyRepository policyRepository;
    private PolicyServiceImpl policyService;
    private PolicyMapper policyMapper;
    private PolicyQueryService policyQueryService;
    private com.claimassist.platform.customer_service.repository.CustomerRepository customerRepository;
    private com.claimassist.platform.customer_service.repository.CoveragePlanRepository coveragePlanRepository;
    private com.claimassist.platform.common_lib.observability.event.EventLogger eventLogger;
    private com.claimassist.platform.common_lib.observability.PerformanceLogger performanceLogger;
    private PolicyServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        policyRepository = mock(PolicyRepository.class);
        policyMapper = mock(PolicyMapper.class);
        policyQueryService = mock(PolicyQueryService.class);
        customerRepository = mock(com.claimassist.platform.customer_service.repository.CustomerRepository.class);
        coveragePlanRepository = mock(com.claimassist.platform.customer_service.repository.CoveragePlanRepository.class);
        eventLogger = mock(com.claimassist.platform.common_lib.observability.event.EventLogger.class);
        performanceLogger = mock(com.claimassist.platform.common_lib.observability.PerformanceLogger.class);
        adapter = mock(PolicyServiceAdapter.class);

        policyService = new PolicyServiceImpl(policyRepository, customerRepository, coveragePlanRepository, policyMapper, policyQueryService, eventLogger, performanceLogger, adapter, mock(com.claimassist.platform.customer_service.config.PolicyServiceProperties.class));
    }

    @Test
    void ownerCancels_delegationInvoked_noDbWrite() {
        Policy p = Policy.builder().id(11L).policyNumber("POL-11").status("ACTIVE").effectiveDate(Instant.parse("2026-01-01T00:00:00Z")).build();
        when(policyRepository.findByIdAndCustomerId(11L, 200L)).thenReturn(Optional.of(p));
        when(adapter.cancelPolicy(eq(11L), any(CancelRequestDto.class), anyString())).thenReturn(Map.of("policyId", 11L, "status", "CANCELLED"));

        var resp = policyService.cancelPolicy(11L, new CancelRequestDto(Instant.parse("2026-02-01T00:00:00Z"), "customer request"), 200L, "Bearer token");
        verify(adapter).cancelPolicy(eq(11L), any(CancelRequestDto.class), eq("Bearer token"));
        verify(policyRepository, never()).save(any());
    }

    @Test
    void nonOwnerCancel_rejected() {
        when(policyRepository.findByIdAndCustomerId(12L, 200L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> policyService.cancelPolicy(12L, new CancelRequestDto(), 200L, "Bearer token")).isInstanceOf(ResourceNotFoundException.class);
        verify(adapter, never()).cancelPolicy(anyLong(), any(), anyString());
    }

    @Test
    void policyService404_propagated_noDbWrite() {
        Policy p = Policy.builder().id(13L).policyNumber("POL-13").status("ACTIVE").effectiveDate(Instant.parse("2026-01-01T00:00:00Z")).build();
        when(policyRepository.findByIdAndCustomerId(13L, 200L)).thenReturn(Optional.of(p));
        when(adapter.cancelPolicy(eq(13L), any(CancelRequestDto.class), anyString())).thenThrow(new com.claimassist.platform.common_lib.error.ResourceNotFoundException("Policy Service resource","13"));

        assertThatThrownBy(() -> policyService.cancelPolicy(13L, new CancelRequestDto(), 200L, "Bearer token")).isInstanceOf(com.claimassist.platform.common_lib.error.ResourceNotFoundException.class);
        verify(policyRepository, never()).save(any());
    }

    @Test
    void ownerReinstate_delegationInvoked_noDbWrite() {
        Policy p = Policy.builder().id(21L).policyNumber("POL-21").status("CANCELLED").effectiveDate(Instant.parse("2026-01-01T00:00:00Z")).build();
        when(policyRepository.findByIdAndCustomerId(21L, 300L)).thenReturn(Optional.of(p));
        when(adapter.reinstatePolicy(eq(21L), any(ReinstateRequestDto.class), anyString(), anyString())).thenReturn(Map.of("policyId", 21L, "status", "REINSTATEMENT_PENDING"));

        var resp = policyService.reinstatePolicy(21L, new ReinstateRequestDto(null), 300L, null, "Bearer token");
        verify(adapter).reinstatePolicy(eq(21L), any(ReinstateRequestDto.class), isNull(), eq("Bearer token"));
        verify(policyRepository, never()).save(any());
    }

    @Test
    void reinstate_missingIdempotency_allowedIfPolicyServiceAccepts() {
        Policy p = Policy.builder().id(22L).policyNumber("POL-22").status("CANCELLED").effectiveDate(Instant.parse("2026-01-01T00:00:00Z")).build();
        when(policyRepository.findByIdAndCustomerId(22L, 300L)).thenReturn(Optional.of(p));
        when(adapter.reinstatePolicy(eq(22L), any(ReinstateRequestDto.class), isNull(), anyString())).thenReturn(Map.of("policyId", 22L, "status", "REINSTATEMENT_PENDING"));

        var resp = policyService.reinstatePolicy(22L, new ReinstateRequestDto(null), 300L, null, "Bearer token");
        verify(adapter).reinstatePolicy(eq(22L), any(ReinstateRequestDto.class), isNull(), eq("Bearer token"));
        verify(policyRepository, never()).save(any());
    }
}
