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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 3: PolicyQueryService read-path behavior. Focus is the coverage
 * read path - the DTO is built from the policy's already-loaded coverage plan
 * (via the @EntityGraph) with a SINGLE repository call (no second, redundant
 * coverage-plan lookup), and ownership is enforced by the query so an
 * unowned policy throws instead of being served.
 */
class PolicyQueryServiceTest {

    private PolicyRepository policyRepository;
    private PolicyMapper policyMapper;
    private PolicyQueryService service;

    private CoveragePlan plan;
    private Policy policy;

    @BeforeEach
    void setUp() {
        policyRepository = mock(PolicyRepository.class);
        policyMapper = mock(PolicyMapper.class);
        com.claimassist.platform.customer_service.client.PolicyServiceAdapter adapter = mock(com.claimassist.platform.customer_service.client.PolicyServiceAdapter.class);
        com.claimassist.platform.customer_service.config.PolicyServiceProperties props = mock(com.claimassist.platform.customer_service.config.PolicyServiceProperties.class);
        when(props.isReadDelegationEnabled()).thenReturn(false);
        service = new PolicyQueryService(policyRepository, policyMapper, adapter, props);

        plan = CoveragePlan.builder()
                .id(5L)
                .name("Comprehensive")
                .productType("HOME")
                .deductibleCents(500_00L)
                .coverageLimitCents(1_000_000_00L)
                .build();

        policy = Policy.builder()
                .id(42L)
                .policyNumber("POL-1001")
                .status("ACTIVE")
                .renewalDate(Instant.parse("2027-01-01T00:00:00Z"))
                .build();
        policy.setCoveragePlan(plan);
    }

    @Test
    void getPolicyCoverageBuildsDtoFromLoadedCoveragePlanWithSingleRepoCall() {
        when(policyRepository.findByIdAndCustomerId(42L, 7L)).thenReturn(Optional.of(policy));

        PolicyCoverageDto dto = service.getPolicyCoverage(42L, 7L);

        assertThat(dto.policyId()).isEqualTo(42L);
        assertThat(dto.policyNumber()).isEqualTo("POL-1001");
        assertThat(dto.status()).isEqualTo("ACTIVE");
        assertThat(dto.productType()).isEqualTo("HOME");
        assertThat(dto.coveragePlanName()).isEqualTo("Comprehensive");
        assertThat(dto.deductibleCents()).isEqualTo(500_00L);
        assertThat(dto.coverageLimitCents()).isEqualTo(1_000_000_00L);
        assertThat(dto.renewalDate()).isEqualTo("2027-01-01T00:00:00Z");

        // Exactly ONE repository call - the coverage plan is already fetched by
        // the @EntityGraph, so there must be no second lookup.
        verify(policyRepository).findByIdAndCustomerId(42L, 7L);
    }

    @Test
    void getPolicyCoverageThrowsWhenPolicyNotOwnedByCaller() {
        when(policyRepository.findByIdAndCustomerId(42L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPolicyCoverage(42L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(policyRepository).findByIdAndCustomerId(42L, 99L);
    }

    @Test
    void getMyPoliciesMapsEachPolicyViaMapper() {
        Policy second = Policy.builder().id(43L).policyNumber("POL-1002").status("PENDING").build();
        second.setCoveragePlan(plan);
        when(policyRepository.findByCustomerId(7L)).thenReturn(List.of(policy, second));
        when(policyMapper.toPolicyResponse(eq(policy))).thenReturn(
                new PolicyResponse(42L, "POL-1001", "ACTIVE", "Comprehensive", "HOME", null, policy.getRenewalDate()));
        when(policyMapper.toPolicyResponse(eq(second))).thenReturn(
                new PolicyResponse(43L, "POL-1002", "PENDING", "Comprehensive", "HOME", null, null));

        List<PolicyResponse> result = service.getMyPolicies(7L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).policyNumber()).isEqualTo("POL-1001");
        assertThat(result.get(1).policyNumber()).isEqualTo("POL-1002");
    }

    @Test
    void getPolicyCoverageMapsNullRenewalDateToNull() {
        policy.setRenewalDate(null);
        when(policyRepository.findByIdAndCustomerId(42L, 7L)).thenReturn(Optional.of(policy));

        PolicyCoverageDto dto = service.getPolicyCoverage(42L, 7L);

        assertThat(dto.renewalDate()).isNull();
    }
}