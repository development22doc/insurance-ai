package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.customer_service.dto.policy.PolicyUpdateRequest;
import com.claimassist.platform.customer_service.entity.CoveragePlan;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.entity.Policy;
import com.claimassist.platform.customer_service.mapper.PolicyMapper;
import com.claimassist.platform.customer_service.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyUpdateSafetyTest {

    private PolicyRepository policyRepository;
    private PolicyMapper policyMapper;
    private PolicyServiceImpl policyService;
    private com.claimassist.platform.customer_service.repository.CustomerRepository customerRepository;
    private com.claimassist.platform.customer_service.repository.CoveragePlanRepository coveragePlanRepository;

    @BeforeEach
    void setUp() {
        policyRepository = mock(PolicyRepository.class);
        policyMapper = mock(PolicyMapper.class);
        customerRepository = mock(com.claimassist.platform.customer_service.repository.CustomerRepository.class);
        coveragePlanRepository = mock(com.claimassist.platform.customer_service.repository.CoveragePlanRepository.class);
        policyService = new PolicyServiceImpl(policyRepository, customerRepository, coveragePlanRepository, policyMapper, mock(PolicyQueryService.class), mock(com.claimassist.platform.common_lib.observability.event.EventLogger.class), mock(com.claimassist.platform.common_lib.observability.PerformanceLogger.class), mock(com.claimassist.platform.customer_service.client.PolicyServiceAdapter.class), mock(com.claimassist.platform.customer_service.config.PolicyServiceProperties.class));
    }

    @Test
    void rejectSettingActiveStatusDirectly() {
        Policy p = Policy.builder().id(1L).policyNumber("POL-1").status("PENDING").effectiveDate(Instant.parse("2026-01-01T00:00:00Z")).build();
        when(policyRepository.findByIdAndCustomerId(1L, 100L)).thenReturn(Optional.of(p));

        PolicyUpdateRequest req = new PolicyUpdateRequest("ACTIVE", null);

        assertThatThrownBy(() -> policyService.updatePolicy(1L, req, 100L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("lifecycle status must be changed");

        verify(policyRepository).findByIdAndCustomerId(1L, 100L);
    }

    @Test
    void rejectSettingCancelledDirectly() {
        Policy p = Policy.builder().id(2L).policyNumber("POL-2").status("ACTIVE").effectiveDate(Instant.parse("2026-01-01T00:00:00Z")).build();
        when(policyRepository.findByIdAndCustomerId(2L, 100L)).thenReturn(Optional.of(p));

        PolicyUpdateRequest req = new PolicyUpdateRequest("CANCELLED", null);

        assertThatThrownBy(() -> policyService.updatePolicy(2L, req, 100L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("lifecycle status must be changed");
    }

    @Test
    void allowSettingLegacyPendingStatus() {
        Policy p = Policy.builder().id(3L).policyNumber("POL-3").status("PENDING").effectiveDate(Instant.parse("2026-01-01T00:00:00Z")).build();
        when(policyRepository.findByIdAndCustomerId(3L, 100L)).thenReturn(Optional.of(p));
        when(policyRepository.save(p)).thenReturn(p);
        when(policyMapper.toPolicyResponse(p)).thenReturn(new com.claimassist.platform.customer_service.dto.policy.PolicyResponse(3L, "POL-3", "PENDING", "Plan", "HOME", p.getEffectiveDate(), null));

        PolicyUpdateRequest req = new PolicyUpdateRequest("PENDING", null);
        var resp = policyService.updatePolicy(3L, req, 100L);
        assertThat(resp.status()).isEqualTo("PENDING");
        verify(policyRepository).save(p);
    }

    @Test
    void allowRenewalDateOnlyUpdate() {
        Policy p = Policy.builder().id(4L).policyNumber("POL-4").status("ACTIVE").effectiveDate(Instant.parse("2026-01-01T00:00:00Z")).build();
        when(policyRepository.findByIdAndCustomerId(4L, 100L)).thenReturn(Optional.of(p));
        when(policyRepository.save(p)).thenReturn(p);
        when(policyMapper.toPolicyResponse(p)).thenReturn(new com.claimassist.platform.customer_service.dto.policy.PolicyResponse(4L, "POL-4", "ACTIVE", "Plan", "AUTO", p.getEffectiveDate(), Instant.parse("2027-01-01T00:00:00Z")));

        PolicyUpdateRequest req = new PolicyUpdateRequest(null, Instant.parse("2027-01-01T00:00:00Z"));
        var resp = policyService.updatePolicy(4L, req, 100L);
        assertThat(resp.renewalDate()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
        verify(policyRepository).save(p);
    }

    @Test
    void updatePolicy_notFound_throws() {
        when(policyRepository.findByIdAndCustomerId(99L, 100L)).thenReturn(Optional.empty());
        PolicyUpdateRequest req = new PolicyUpdateRequest(null, null);
        assertThatThrownBy(() -> policyService.updatePolicy(99L, req, 100L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
