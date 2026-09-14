package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.policy_service.dto.CustomerPolicyDetailDto;
import com.claimassist.platform.policy_service.dto.CustomerPolicySummaryDto;
import com.claimassist.platform.policy_service.entity.CustomerPolicy;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.entity.ProductType;
import com.claimassist.platform.policy_service.repository.CustomerPolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerPolicyReadServiceTest {

    @Mock
    private CustomerPolicyRepository customerPolicyRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private CustomerPolicyReadService customerPolicyReadService;

    @Test
    void getMyPolicies_returnsOnlyCurrentCustomerPolicies() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);

        Product product = Product.builder()
                .id(10L)
                .code("AUTO")
                .name("Auto Insurance")
                .type(ProductType.AUTO)
                .status("ACTIVE")
                .description("Vehicle policy")
                .build();

        Plan plan = Plan.builder()
                .id(20L)
                .product(product)
                .code("AUTO_BASIC")
                .name("Basic Auto")
                .status("ACTIVE")
                .annualPremiumCents(25000L)
                .deductibleCents(4000L)
                .coverageLimitCents(500000L)
                .currency("INR")
                .build();

        CustomerPolicy policy = CustomerPolicy.builder()
                .id(101L)
                .customerId(42L)
                .plan(plan)
                .policyNumber("POL-101")
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .renewalDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        when(customerPolicyRepository.findByCustomerIdOrderByEffectiveDateDesc(42L)).thenReturn(List.of(policy));

        List<CustomerPolicySummaryDto> policies = customerPolicyReadService.getMyPolicies();

        assertThat(policies).hasSize(1);
        assertThat(policies.getFirst().policyNumber()).isEqualTo("POL-101");
        assertThat(policies.getFirst().productName()).isEqualTo("Auto Insurance");
        assertThat(policies.getFirst().planName()).isEqualTo("Basic Auto");
    }

    @Test
    void getMyPolicy_returnsOwnedPolicy() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);

        Product product = Product.builder()
                .id(11L)
                .code("TRAVEL")
                .name("Travel Insurance")
                .type(ProductType.TRAVEL)
                .status("ACTIVE")
                .description("Travel cover")
                .build();

        Plan plan = Plan.builder()
                .id(21L)
                .product(product)
                .code("TRAVEL_PLUS")
                .name("Travel Plus")
                .status("ACTIVE")
                .annualPremiumCents(12000L)
                .deductibleCents(2000L)
                .coverageLimitCents(300000L)
                .currency("INR")
                .build();

        CustomerPolicy policy = CustomerPolicy.builder()
                .id(77L)
                .customerId(42L)
                .plan(plan)
                .policyNumber("POL-77")
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-02-01T00:00:00Z"))
                .renewalDate(Instant.parse("2026-02-01T00:00:00Z"))
                .build();

        when(customerPolicyRepository.findByIdAndCustomerId(77L, 42L)).thenReturn(Optional.of(policy));

        CustomerPolicyDetailDto detail = customerPolicyReadService.getMyPolicy(77L);

        assertThat(detail.policyNumber()).isEqualTo("POL-77");
        assertThat(detail.productName()).isEqualTo("Travel Insurance");
        assertThat(detail.currency()).isEqualTo("INR");
    }

    @Test
    void getMyPolicy_rejectsNonOwnedPolicy() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);
        when(customerPolicyRepository.findByIdAndCustomerId(99L, 42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerPolicyReadService.getMyPolicy(99L));
    }

    @Test
    void cacheKeys_areUniquePerCustomerAndPolicyId() {
        assertThat(CustomerPolicyReadService.customerPoliciesListKey(1L))
                .isNotEqualTo(CustomerPolicyReadService.customerPoliciesListKey(2L));
        assertThat(CustomerPolicyReadService.customerPolicyDetailKey(1L, 101L))
                .isNotEqualTo(CustomerPolicyReadService.customerPolicyDetailKey(2L, 101L));
        assertThat(CustomerPolicyReadService.customerPolicyDetailKey(1L, 101L))
                .isNotEqualTo(CustomerPolicyReadService.customerPolicyDetailKey(1L, 202L));
    }
}
