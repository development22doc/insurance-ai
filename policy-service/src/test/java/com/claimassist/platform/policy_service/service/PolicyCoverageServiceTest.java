package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyCoverageServiceTest {

    @Mock
    private PolicyContractRepository policyContractRepository;

    @Mock
    private PolicyPeriodRepository policyPeriodRepository;

    @Mock
    private PlanRepository planRepository;

    @InjectMocks
    private PolicyCoverageService policyCoverageService;

    @Test
    void getCoverageForCustomer_returnsCoverageWhenIncidentFallsWithinPolicyWindow() {
        Product product = Product.builder().id(7L).name("Auto").type(ProductType.AUTO).status("ACTIVE").build();
        Plan plan = Plan.builder().id(8L).product(product).name("Comprehensive").status("ACTIVE").annualPremiumCents(50000L).currency("INR").coverageLimitCents(1000000L).deductibleCents(25000L).build();
        PolicyContract contract = PolicyContract.builder().id(99L).customerId(42L).productId(7L).policyNumber("POL-42-99").status("ACTIVE").build();
        PolicyPeriod period = PolicyPeriod.builder().id(77L).policyContract(contract).planId(8L).renewalSequence(0).status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z")).expirationDate(Instant.parse("2026-01-01T00:00:00Z")).renewalDate(Instant.parse("2026-01-01T00:00:00Z")).build();

        when(policyContractRepository.findByIdAndCustomerId(99L, 42L)).thenReturn(Optional.of(contract));
        when(policyPeriodRepository.findCoveringPeriodsForContractAt(99L, Instant.parse("2025-06-01T00:00:00Z"))).thenReturn(List.of(period));
        when(planRepository.findById(8L)).thenReturn(Optional.of(plan));

        PolicyCoverageDto dto = policyCoverageService.getCoverageForCustomer(99L, 42L, Instant.parse("2025-06-01T00:00:00Z"));

        assertThat(dto.policyId()).isEqualTo(99L);
        assertThat(dto.policyNumber()).isEqualTo("POL-42-99");
        assertThat(dto.productType()).isEqualTo("AUTO");
        assertThat(dto.coveragePlanName()).isEqualTo("Comprehensive");
        assertThat(dto.coverageLimitCents()).isEqualTo(1000000L);
    }

    @Test
    void resolveCoveringPeriod_throwsWhenIncidentFallsOutsideCoverageWindow() {
        PolicyContract contract = PolicyContract.builder().id(99L).customerId(42L).productId(7L).policyNumber("POL-42-99").status("ACTIVE").build();
        when(policyPeriodRepository.findCoveringPeriodsForContractAt(any(Long.class), any(Instant.class))).thenReturn(List.of());

        assertThatThrownBy(() -> policyCoverageService.resolveCoveringPeriod(99L, Instant.parse("2026-01-01T00:00:00Z")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void asOfCoverage_includesEffectiveDateBoundary() {
        Product product = Product.builder().id(7L).name("Auto").type(ProductType.AUTO).status("ACTIVE").build();
        Plan plan = Plan.builder().id(8L).product(product).name("Comprehensive").status("ACTIVE").annualPremiumCents(50000L).currency("INR").coverageLimitCents(1000000L).deductibleCents(25000L).build();
        PolicyContract contract = PolicyContract.builder().id(99L).customerId(42L).productId(7L).policyNumber("POL-42-99").status("ACTIVE").build();
        PolicyPeriod period = PolicyPeriod.builder().id(77L).policyContract(contract).planId(8L).renewalSequence(0).status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z")).expirationDate(Instant.parse("2026-01-01T00:00:00Z")).renewalDate(Instant.parse("2026-01-01T00:00:00Z")).build();

        when(policyContractRepository.findByIdAndCustomerId(99L, 42L)).thenReturn(Optional.of(contract));
        when(policyPeriodRepository.findCoveringPeriodsForContractAt(99L, Instant.parse("2025-01-01T00:00:00Z"))).thenReturn(List.of(period));
        when(planRepository.findById(8L)).thenReturn(Optional.of(plan));

        PolicyCoverageDto dto = policyCoverageService.getCoverageForCustomer(99L, 42L, Instant.parse("2025-01-01T00:00:00Z"));

        assertThat(dto.policyId()).isEqualTo(99L);
    }

    @Test
    void asOfCoverage_includesJustBeforeExpirationDate() {
        Product product = Product.builder().id(7L).name("Auto").type(ProductType.AUTO).status("ACTIVE").build();
        Plan plan = Plan.builder().id(8L).product(product).name("Comprehensive").status("ACTIVE").annualPremiumCents(50000L).currency("INR").coverageLimitCents(1000000L).deductibleCents(25000L).build();
        PolicyContract contract = PolicyContract.builder().id(99L).customerId(42L).productId(7L).policyNumber("POL-42-99").status("ACTIVE").build();
        PolicyPeriod period = PolicyPeriod.builder().id(77L).policyContract(contract).planId(8L).renewalSequence(0).status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z")).expirationDate(Instant.parse("2026-01-01T00:00:00Z")).renewalDate(Instant.parse("2026-01-01T00:00:00Z")).build();

        when(policyContractRepository.findByIdAndCustomerId(99L, 42L)).thenReturn(Optional.of(contract));
        when(policyPeriodRepository.findCoveringPeriodsForContractAt(99L, Instant.parse("2025-12-31T23:59:59Z"))).thenReturn(List.of(period));
        when(planRepository.findById(8L)).thenReturn(Optional.of(plan));

        PolicyCoverageDto dto = policyCoverageService.getCoverageForCustomer(99L, 42L, Instant.parse("2025-12-31T23:59:59Z"));

        assertThat(dto.policyId()).isEqualTo(99L);
    }

    @Test
    void asOfCoverage_excludesExactExpirationDate() {
        PolicyContract contract = PolicyContract.builder().id(99L).customerId(42L).productId(7L).policyNumber("POL-42-99").status("ACTIVE").build();
        when(policyPeriodRepository.findCoveringPeriodsForContractAt(99L, Instant.parse("2026-01-01T00:00:00Z"))).thenReturn(List.of());

        assertThatThrownBy(() -> policyCoverageService.resolveCoveringPeriod(99L, Instant.parse("2026-01-01T00:00:00Z")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void asOfCoverage_throwsBeforePolicyStart() {
        PolicyContract contract = PolicyContract.builder().id(99L).customerId(42L).productId(7L).policyNumber("POL-42-99").status("ACTIVE").build();
        when(policyPeriodRepository.findCoveringPeriodsForContractAt(99L, Instant.parse("2024-12-31T23:59:59Z"))).thenReturn(List.of());

        assertThatThrownBy(() -> policyCoverageService.resolveCoveringPeriod(99L, Instant.parse("2024-12-31T23:59:59Z")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void asOfCoverage_throwsAfterPolicyExpires() {
        PolicyContract contract = PolicyContract.builder().id(99L).customerId(42L).productId(7L).policyNumber("POL-42-99").status("ACTIVE").build();
        when(policyPeriodRepository.findCoveringPeriodsForContractAt(99L, Instant.parse("2026-01-01T00:00:01Z"))).thenReturn(List.of());

        assertThatThrownBy(() -> policyCoverageService.resolveCoveringPeriod(99L, Instant.parse("2026-01-01T00:00:01Z")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void asOfCoverage_handlesAdjacentPeriodsCorrectly() {
        Product product = Product.builder().id(7L).name("Auto").type(ProductType.AUTO).status("ACTIVE").build();
        Plan plan = Plan.builder().id(8L).product(product).name("Comprehensive").status("ACTIVE").annualPremiumCents(50000L).currency("INR").coverageLimitCents(1000000L).deductibleCents(25000L).build();
        PolicyContract contract = PolicyContract.builder().id(99L).customerId(42L).productId(7L).policyNumber("POL-42-99").status("ACTIVE").build();

        PolicyPeriod period1 = PolicyPeriod.builder().id(77L).policyContract(contract).planId(8L).renewalSequence(0).status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z")).expirationDate(Instant.parse("2026-01-01T00:00:00Z")).renewalDate(Instant.parse("2026-01-01T00:00:00Z")).build();

        PolicyPeriod period2 = PolicyPeriod.builder().id(78L).policyContract(contract).planId(8L).renewalSequence(1).status("ACTIVE")
                .effectiveDate(Instant.parse("2026-01-01T00:00:00Z")).expirationDate(Instant.parse("2027-01-01T00:00:00Z")).renewalDate(Instant.parse("2027-01-01T00:00:00Z")).build();

        when(policyContractRepository.findByIdAndCustomerId(99L, 42L)).thenReturn(Optional.of(contract));
        when(policyPeriodRepository.findCoveringPeriodsForContractAt(99L, Instant.parse("2025-12-31T23:59:59Z"))).thenReturn(List.of(period1));
        when(policyPeriodRepository.findCoveringPeriodsForContractAt(99L, Instant.parse("2026-01-01T00:00:00Z"))).thenReturn(List.of(period2));
        when(planRepository.findById(8L)).thenReturn(Optional.of(plan));

        PolicyCoverageDto dto1 = policyCoverageService.getCoverageForCustomer(99L, 42L, Instant.parse("2025-12-31T23:59:59Z"));
        assertThat(dto1.policyId()).isEqualTo(99L);

        PolicyCoverageDto dto2 = policyCoverageService.getCoverageForCustomer(99L, 42L, Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(dto2.policyId()).isEqualTo(99L);
    }
}
