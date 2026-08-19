package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.customer_service.dto.policy.PolicyCreateRequest;
import com.claimassist.platform.customer_service.dto.policy.PolicyResponse;
import com.claimassist.platform.customer_service.dto.policy.PolicyUpdateRequest;
import com.claimassist.platform.customer_service.entity.CoveragePlan;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.entity.Policy;
import com.claimassist.platform.customer_service.mapper.PolicyMapper;
import com.claimassist.platform.customer_service.repository.CoveragePlanRepository;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import com.claimassist.platform.customer_service.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyServiceImplTest {

    @Mock private PolicyRepository policyRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CoveragePlanRepository coveragePlanRepository;
    @Mock private PolicyMapper policyMapper;
    @Mock private PolicyQueryService policyQueryService;
    @Mock private EventLogger eventLogger;
    @Mock private PerformanceLogger performanceLogger;

    @InjectMocks private PolicyServiceImpl policyService;

    private Customer customer;
    private CoveragePlan plan;
    private Policy policy;
    private PolicyResponse response;

    @BeforeEach
    void setUp() {
        customer = Customer.builder().id(1L).username("alice@example.com").build();
        plan = CoveragePlan.builder().id(2L).name("Comprehensive").productType("AUTO").build();
        policy = Policy.builder()
                .id(10L)
                .customer(customer)
                .coveragePlan(plan)
                .policyNumber("POL-12345678")
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2026-01-01T00:00:00Z"))
                .renewalDate(Instant.parse("2027-01-01T00:00:00Z"))
                .build();
        response = new PolicyResponse(10L, "POL-12345678", "ACTIVE", "Comprehensive", "AUTO",
                policy.getEffectiveDate(), policy.getRenewalDate());
    }

    @Test
    void createPolicy_success_returnsMappedResponse() {
        PolicyCreateRequest request = new PolicyCreateRequest(2L, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(coveragePlanRepository.findById(2L)).thenReturn(Optional.of(plan));
        when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> {
            Policy p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });
        when(policyMapper.toPolicyResponse(any(Policy.class))).thenReturn(response);

        PolicyResponse result = policyService.createPolicy(request, 1L);

        assertThat(result.policyNumber()).startsWith("POL-");
        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(policyQueryService).evictMyPolicies(1L);
        verify(eventLogger, times(2)).logBusinessEvent(eq("customer-service"), eq("customer-service"), any());
    }

    @Test
    void createPolicy_customerNotFound_throwsResourceNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policyService.createPolicy(
                new PolicyCreateRequest(2L, Instant.parse("2026-01-01T00:00:00Z"), null), 99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(policyRepository, never()).save(any());
    }

    @Test
    void createPolicy_coveragePlanNotFound_throwsResourceNotFound() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(coveragePlanRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policyService.createPolicy(
                new PolicyCreateRequest(77L, Instant.parse("2026-01-01T00:00:00Z"), null), 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(policyRepository, never()).save(any());
    }

    @Test
    void createPolicy_renewalBeforeEffective_throwsBadRequest() {
        PolicyCreateRequest request = new PolicyCreateRequest(2L, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2025-12-01T00:00:00Z"));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(coveragePlanRepository.findById(2L)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> policyService.createPolicy(request, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Renewal date cannot be before effective date");

        verify(policyRepository, never()).save(any());
    }

    @Test
    void createPolicy_saveFailure_rethrowsAfterLoggingFailure() {
        PolicyCreateRequest request = new PolicyCreateRequest(2L, Instant.parse("2026-01-01T00:00:00Z"), null);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(coveragePlanRepository.findById(2L)).thenReturn(Optional.of(plan));
        when(policyRepository.save(any(Policy.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> policyService.createPolicy(request, 1L))
                .isInstanceOf(IllegalStateException.class);

        verify(eventLogger, times(2)).logBusinessEvent(eq("customer-service"), eq("customer-service"), any());
    }

    @Test
    void getPolicyById_success_returnsMappedResponse() {
        when(policyRepository.findByIdAndCustomerId(10L, 1L)).thenReturn(Optional.of(policy));
        when(policyMapper.toPolicyResponse(policy)).thenReturn(response);

        PolicyResponse result = policyService.getPolicyById(10L, 1L);

        assertThat(result.policyNumber()).isEqualTo("POL-12345678");
        verify(performanceLogger).log(eq("BUSINESS"), eq("policy.get_by_id"), anyLong(), any());
    }

    @Test
    void getPolicyById_notFound_throwsResourceNotFound() {
        when(policyRepository.findByIdAndCustomerId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policyService.getPolicyById(10L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getPolicies_mapsPage() {
        when(policyRepository.findByCustomerId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(policy)));
        when(policyMapper.toPolicyResponse(any(Policy.class))).thenReturn(response);

        Page<PolicyResponse> result = policyService.getPolicies(1L, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).policyNumber()).isEqualTo("POL-12345678");
    }

    @Test
    void updatePolicy_success_updatesStatusAndRenewal() {
        when(policyRepository.findByIdAndCustomerId(10L, 1L)).thenReturn(Optional.of(policy));
        when(policyRepository.save(any(Policy.class))).thenReturn(policy);
        when(policyMapper.toPolicyResponse(any(Policy.class))).thenReturn(response);

        PolicyResponse result = policyService.updatePolicy(10L,
                new PolicyUpdateRequest("CANCELLED", Instant.parse("2027-06-01T00:00:00Z")), 1L);

        assertThat(result).isNotNull();
        assertThat(policy.getStatus()).isEqualTo("CANCELLED");
        assertThat(policy.getRenewalDate()).isEqualTo(Instant.parse("2027-06-01T00:00:00Z"));
        verify(policyQueryService).evictMyPolicies(1L);
        verify(policyQueryService).evictPolicyCoverage(10L, 1L);
    }

    @Test
    void updatePolicy_statusOnly_keepsOtherFields() {
        when(policyRepository.findByIdAndCustomerId(10L, 1L)).thenReturn(Optional.of(policy));
        when(policyRepository.save(any(Policy.class))).thenReturn(policy);
        when(policyMapper.toPolicyResponse(any(Policy.class))).thenReturn(response);

        policyService.updatePolicy(10L, new PolicyUpdateRequest("LAPSED", null), 1L);

        assertThat(policy.getStatus()).isEqualTo("LAPSED");
        assertThat(policy.getRenewalDate()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
    }

    @Test
    void updatePolicy_renewalBeforeEffective_throwsBadRequest() {
        when(policyRepository.findByIdAndCustomerId(10L, 1L)).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> policyService.updatePolicy(10L,
                new PolicyUpdateRequest(null, Instant.parse("2025-06-01T00:00:00Z")), 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Renewal date cannot be before effective date");

        verify(policyRepository, never()).save(any());
    }

    @Test
    void updatePolicy_notFound_throwsResourceNotFound() {
        when(policyRepository.findByIdAndCustomerId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policyService.updatePolicy(10L, new PolicyUpdateRequest(null, null), 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletePolicy_success() {
        when(policyRepository.findByIdAndCustomerId(10L, 1L)).thenReturn(Optional.of(policy));

        policyService.deletePolicy(10L, 1L);

        verify(policyRepository).deleteById(10L);
        verify(policyQueryService).evictMyPolicies(1L);
        verify(policyQueryService).evictPolicyCoverage(10L, 1L);
    }

    @Test
    void deletePolicy_notFound_throwsResourceNotFound() {
        when(policyRepository.findByIdAndCustomerId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policyService.deletePolicy(10L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(policyRepository, never()).deleteById(anyLong());
    }

    @Test
    void deletePolicy_failure_rethrowsAfterLogging() {
        when(policyRepository.findByIdAndCustomerId(10L, 1L)).thenReturn(Optional.of(policy));
        org.mockito.Mockito.doThrow(new IllegalStateException("db down"))
                .when(policyRepository).deleteById(10L);

        assertThatThrownBy(() -> policyService.deletePolicy(10L, 1L))
                .isInstanceOf(IllegalStateException.class);

        verify(eventLogger, times(2)).logBusinessEvent(eq("customer-service"), eq("customer-service"), any());
    }
}