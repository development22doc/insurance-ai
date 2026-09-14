package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.policy_service.dto.PolicyContractDetailDto;
import com.claimassist.platform.policy_service.dto.PolicyContractSummaryDto;
import com.claimassist.platform.policy_service.dto.PolicyPeriodDto;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.PolicyContract;
import com.claimassist.platform.policy_service.entity.PolicyPeriod;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.entity.ProductType;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.PolicyContractRepository;
import com.claimassist.platform.policy_service.repository.PolicyPeriodRepository;
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
class PolicyContractReadServiceTest {

    @Mock
    private PolicyContractRepository policyContractRepository;

    @Mock
    private PolicyPeriodRepository policyPeriodRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private PolicyContractReadService policyContractReadService;

    @Test
    void getPoliciesForCustomer_returnsCurrentPeriodMetadata() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);

        Product product = Product.builder()
                .id(10L)
                .code("AUTO")
                .name("Auto Insurance")
                .type(ProductType.AUTO)
                .status("ACTIVE")
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

        PolicyPeriod currentPeriod = PolicyPeriod.builder()
                .id(111L)
                .planId(20L)
                .renewalSequence(0)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .renewalDate(Instant.parse("2026-01-01T00:00:00Z"))
                .activatedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build();

        PolicyContract contract = PolicyContract.builder()
                .id(7L)
                .customerId(42L)
                .productId(10L)
                .policyNumber("POL-7")
                .status("ACTIVE")
                .currentPolicyPeriod(currentPeriod)
                .build();
        currentPeriod.setPolicyContract(contract);

        when(policyContractRepository.findByCustomerIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(contract));
        when(planRepository.findById(20L)).thenReturn(Optional.of(plan));

        List<PolicyContractSummaryDto> policies = policyContractReadService.getMyPolicies();

        assertThat(policies).hasSize(1);
        assertThat(policies.getFirst().currentPolicyPeriodId()).isEqualTo(111L);
        assertThat(policies.getFirst().productName()).isEqualTo("Auto Insurance");
        assertThat(policies.getFirst().planName()).isEqualTo("Basic Auto");
    }

    @Test
    void getPolicyForCustomer_returnsContractDetail() {
        Product product = Product.builder()
                .id(55L)
                .code("TRAVEL")
                .name("Travel Insurance")
                .type(ProductType.TRAVEL)
                .status("ACTIVE")
                .build();

        Plan plan = Plan.builder()
                .id(77L)
                .product(product)
                .code("TRAVEL_PLUS")
                .name("Travel Plus")
                .status("ACTIVE")
                .annualPremiumCents(12000L)
                .deductibleCents(2000L)
                .coverageLimitCents(300000L)
                .currency("INR")
                .build();

        PolicyPeriod currentPeriod = PolicyPeriod.builder()
                .id(222L)
                .planId(77L)
                .renewalSequence(1)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-02-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-02-01T00:00:00Z"))
                .renewalDate(Instant.parse("2026-02-01T00:00:00Z"))
                .activatedAt(Instant.parse("2025-02-01T00:00:00Z"))
                .build();

        PolicyContract contract = PolicyContract.builder()
                .id(9L)
                .customerId(42L)
                .productId(55L)
                .policyNumber("POL-9")
                .status("ACTIVE")
                .currentPolicyPeriod(currentPeriod)
                .build();
        currentPeriod.setPolicyContract(contract);

        when(policyContractRepository.findByIdAndCustomerId(9L, 42L)).thenReturn(Optional.of(contract));
        when(planRepository.findById(77L)).thenReturn(Optional.of(plan));

        PolicyContractDetailDto detail = policyContractReadService.getPolicyForCustomer(42L, 9L);

        assertThat(detail.policyNumber()).isEqualTo("POL-9");
        assertThat(detail.currentPolicyPeriodId()).isEqualTo(222L);
        assertThat(detail.productName()).isEqualTo("Travel Insurance");
    }

    @Test
    void getPeriodsForPolicy_ordersByRenewalSequenceAsc() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);

        PolicyContract contract = PolicyContract.builder()
                .id(7L)
                .customerId(42L)
                .productId(10L)
                .policyNumber("POL-7")
                .status("ACTIVE")
                .build();

        PolicyPeriod first = PolicyPeriod.builder()
                .id(10L)
                .policyContract(contract)
                .planId(20L)
                .renewalSequence(0)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .renewalDate(Instant.parse("2026-01-01T00:00:00Z"))
                .activatedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build();

        PolicyPeriod second = PolicyPeriod.builder()
                .id(11L)
                .policyContract(contract)
                .planId(20L)
                .renewalSequence(1)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2026-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2027-01-01T00:00:00Z"))
                .renewalDate(Instant.parse("2027-01-01T00:00:00Z"))
                .activatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        when(policyContractRepository.findByIdAndCustomerId(7L, 42L)).thenReturn(Optional.of(contract));
        when(policyPeriodRepository.findByPolicyContractIdOrderByRenewalSequenceAsc(7L)).thenReturn(List.of(first, second));
        when(planRepository.findById(20L)).thenReturn(Optional.of(Plan.builder().id(20L).name("Basic Auto").build()));

        List<PolicyPeriodDto> periods = policyContractReadService.getPeriodsForPolicy(7L);

        assertThat(periods).hasSize(2);
        assertThat(periods.getFirst().renewalSequence()).isEqualTo(0);
        assertThat(periods.get(1).renewalSequence()).isEqualTo(1);
    }

    @Test
    void getMyPolicy_rejectsNonOwnedPolicy() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);
        when(policyContractRepository.findByIdAndCustomerId(99L, 42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> policyContractReadService.getMyPolicy(99L));
    }
}
