package com.claimassist.platform.policy_service.service.impl;

import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.dto.PolicySummaryDto;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.Policy;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.repository.PolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyLookupServiceImplTest {

    @Mock
    PolicyRepository policyRepository;

    @InjectMocks
    PolicyLookupServiceImpl service;

    @Test
    void getPolicy_returnsSummaryForOwner() {
        Product product = new Product();
        product.setCode("AUTO");

        Plan plan = new Plan();
        plan.setProduct(product);
        plan.setName("STANDARD_PLAN");

        Policy policy = new Policy();
        policy.setId(1L);
        policy.setPolicyNumber("POL-1");
        policy.setCustomerId(7L);
        policy.setCoveragePlan(plan);
        policy.setStatus("ACTIVE");
        policy.setEffectiveDate(Instant.parse("2025-01-01T00:00:00Z"));
        policy.setRenewalDate(Instant.parse("2026-01-01T00:00:00Z"));

        when(policyRepository.findByIdAndCustomerId(1L, 7L)).thenReturn(Optional.of(policy));

        PolicySummaryDto dto = service.getPolicy(1L, 7L);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.productType()).isEqualTo("AUTO");
        assertThat(dto.coveragePlanName()).isEqualTo("STANDARD_PLAN");
    }

    @Test
    void getPolicy_throwsIfNotOwnedByCustomer() {
        when(policyRepository.findByIdAndCustomerId(1L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPolicy(1L, 7L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getPoliciesForCustomer_rejectsDifferentCustomer() {
        assertThatThrownBy(() -> service.getPoliciesForCustomer(7L, 9L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getPoliciesForCustomer_returnsPoliciesOrderedNewestFirst() {
        Product product = new Product();
        product.setCode("AUTO");

        Plan plan = new Plan();
        plan.setProduct(product);
        plan.setName("STANDARD_PLAN");

        Policy policy = new Policy();
        policy.setId(1L);
        policy.setPolicyNumber("POL-1");
        policy.setCustomerId(7L);
        policy.setCoveragePlan(plan);
        policy.setStatus("ACTIVE");
        policy.setEffectiveDate(Instant.parse("2025-01-01T00:00:00Z"));
        policy.setRenewalDate(Instant.parse("2026-01-01T00:00:00Z"));

        when(policyRepository.findByCustomerIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(policy));

        List<PolicySummaryDto> list = service.getPoliciesForCustomer(7L, 7L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).policyNumber()).isEqualTo("POL-1");
    }
}
