package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.customer_service.dto.policy.PolicyResponse;
import com.claimassist.platform.customer_service.entity.CoveragePlan;
import com.claimassist.platform.customer_service.entity.Policy;
import com.claimassist.platform.customer_service.mapper.PolicyMapper;
import com.claimassist.platform.customer_service.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyReadDelegationTest {

    private PolicyRepository policyRepository;
    private PolicyMapper policyMapper;
    private com.claimassist.platform.customer_service.client.PolicyServiceAdapter adapter;
    private com.claimassist.platform.customer_service.config.PolicyServiceProperties props;
    private PolicyServiceImpl policyService;
    private PolicyQueryService queryService;

    @BeforeEach
    void setUp() {
        policyRepository = mock(PolicyRepository.class);
        policyMapper = mock(PolicyMapper.class);
        adapter = mock(com.claimassist.platform.customer_service.client.PolicyServiceAdapter.class);
        props = mock(com.claimassist.platform.customer_service.config.PolicyServiceProperties.class);

        policyService = new PolicyServiceImpl(policyRepository, mock(com.claimassist.platform.customer_service.repository.CustomerRepository.class), mock(com.claimassist.platform.customer_service.repository.CoveragePlanRepository.class), policyMapper, mock(PolicyQueryService.class), mock(com.claimassist.platform.common_lib.observability.event.EventLogger.class), mock(com.claimassist.platform.common_lib.observability.PerformanceLogger.class), adapter, props);
        queryService = new PolicyQueryService(policyRepository, policyMapper, adapter, props);
    }

    @Test
    void whenDelegationEnabled_andPolicyServiceReturns404_thenResourceNotFoundAndNoRepoCall() {
        when(props.isReadDelegationEnabled()).thenReturn(true);
        long policyId = 99L;
        long actingUser = 7L;

        when(adapter.getPolicyById(policyId, actingUser)).thenThrow(new ResourceNotFoundException("Policy", String.valueOf(policyId)));

        assertThatThrownBy(() -> policyService.getPolicyById(policyId, actingUser))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(adapter, times(1)).getPolicyById(policyId, actingUser);
        verify(policyRepository, times(0)).findByIdAndCustomerId(policyId, actingUser);
    }

    @Test
    void whenDelegationEnabled_andPolicyServiceReturns403_thenAccessDeniedAndNoRepoCall() {
        when(props.isReadDelegationEnabled()).thenReturn(true);
        long policyId = 100L;
        long actingUser = 7L;

        when(adapter.getPolicyById(policyId, actingUser)).thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> policyService.getPolicyById(policyId, actingUser))
                .isInstanceOf(AccessDeniedException.class);

        verify(adapter, times(1)).getPolicyById(policyId, actingUser);
        verify(policyRepository, times(0)).findByIdAndCustomerId(policyId, actingUser);
    }

    @Test
    void whenDelegationEnabled_andPolicyServiceUnavailable_thenServiceUnavailableAndNoRepoCall() {
        when(props.isReadDelegationEnabled()).thenReturn(true);
        long policyId = 101L;
        long actingUser = 7L;

        when(adapter.getPolicyById(policyId, actingUser)).thenThrow(new com.claimassist.platform.common_lib.error.ServiceUnavailableException("down"));

        assertThatThrownBy(() -> policyService.getPolicyById(policyId, actingUser))
                .isInstanceOf(com.claimassist.platform.common_lib.error.ServiceUnavailableException.class);

        verify(adapter, times(1)).getPolicyById(policyId, actingUser);
        verify(policyRepository, times(0)).findByIdAndCustomerId(policyId, actingUser);
    }

    @Test
    void delegatedPolicyByIdSuccess_mapsCorrectly_andDoesNotCallRepo() {
        when(props.isReadDelegationEnabled()).thenReturn(true);
        long policyId = 200L;
        long actingUser = 7L;

        PolicyResponse resp = new PolicyResponse(policyId, "POL-200", "ACTIVE", "Plan A", "HOME", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"));
        when(adapter.getPolicyById(policyId, actingUser)).thenReturn(resp);

        PolicyResponse got = policyService.getPolicyById(policyId, actingUser);
        assertThat(got).isNotNull();
        assertThat(got.policyNumber()).isEqualTo("POL-200");
        assertThat(got.status()).isEqualTo("ACTIVE");

        verify(adapter, times(1)).getPolicyById(policyId, actingUser);
        verify(policyRepository, times(0)).findByIdAndCustomerId(policyId, actingUser);
    }

    @Test
    void delegatedCustomerPolicyListSuccess_mapsCorrectly_andDoesNotCallRepo() {
        when(props.isReadDelegationEnabled()).thenReturn(true);
        long customerId = 7L;

        PolicyResponse p1 = new PolicyResponse(201L, "POL-201", "ACTIVE", "Plan A", "HOME", Instant.parse("2026-02-01T00:00:00Z"), null);
        PolicyResponse p2 = new PolicyResponse(202L, "POL-202", "PENDING", "Plan A", "HOME", Instant.parse("2026-03-01T00:00:00Z"), null);

        when(adapter.getPoliciesForCustomer(customerId, customerId)).thenReturn(List.of(p1, p2));

        // PolicyServiceImpl.getPolicies delegates and wraps as Page
        org.springframework.data.domain.Page<PolicyResponse> page = policyService.getPolicies(customerId, org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).policyNumber()).isEqualTo("POL-201");

        verify(adapter, times(1)).getPoliciesForCustomer(customerId, customerId);
        verify(policyRepository, times(0)).findByCustomerId(customerId, org.springframework.data.domain.Pageable.unpaged());
    }

    @Test
    void delegatedCoverageSuccess_returnsDto_andDoesNotCallRepo() {
        when(props.isReadDelegationEnabled()).thenReturn(true);
        long policyId = 301L;
        long actingUser = 7L;

        PolicyCoverageDto dto = new PolicyCoverageDto(policyId, "POL-301", "ACTIVE", "HOME", "Comprehensive", 500_00L, 1_000_000_00L, "2027-01-01T00:00:00Z");
        when(adapter.getPolicyCoverage(policyId, actingUser)).thenReturn(dto);

        PolicyCoverageDto got = queryService.getPolicyCoverage(policyId, actingUser);
        assertThat(got).isNotNull();
        assertThat(got.policyNumber()).isEqualTo("POL-301");

        verify(adapter, times(1)).getPolicyCoverage(policyId, actingUser);
        verify(policyRepository, times(0)).findByIdAndCustomerId(policyId, actingUser);
    }
}
