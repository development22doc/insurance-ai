package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.config.RedisCacheConfig;
import com.claimassist.platform.policy_service.dto.RenewalInitiationResponse;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.PolicyContract;
import com.claimassist.platform.policy_service.entity.PolicyPeriod;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.entity.Purchase;
import com.claimassist.platform.policy_service.entity.PurchaseStatus;
import com.claimassist.platform.policy_service.entity.PurchaseType;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.PolicyContractRepository;
import com.claimassist.platform.policy_service.repository.PolicyPeriodRepository;
import com.claimassist.platform.policy_service.repository.PurchaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyLifecycleServiceTest {

    @Mock
    private PolicyContractRepository policyContractRepository;

    @Mock
    private PolicyPeriodRepository policyPeriodRepository;

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private StripePaymentGateway stripePaymentGateway;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private PolicyLifecycleService policyLifecycleService;

    @Test
    void createInitialPolicyForPurchase_createsContractAndInitialPeriodOnPaidPurchase() {
        Product product = Product.builder().id(11L).name("Auto").status("ACTIVE").build();
        Plan plan = Plan.builder().id(22L).product(product).name("Comprehensive").status("ACTIVE").annualPremiumCents(50000L).currency("INR").coverageLimitCents(1000000L).deductibleCents(25000L).build();
        Purchase purchase = Purchase.builder().id(101L).customerId(42L).plan(plan).build();
        Instant confirmedAt = Instant.parse("2025-01-15T00:00:00Z");

        when(policyContractRepository.findByCustomerIdAndPolicyNumber(42L, "POL-42-101")).thenReturn(Optional.empty());
        when(policyContractRepository.saveAndFlush(any(PolicyContract.class))).thenAnswer(invocation -> {
            PolicyContract contract = invocation.getArgument(0);
            contract.setId(501L);
            return contract;
        });
        when(policyContractRepository.save(any(PolicyContract.class))).thenAnswer(invocation -> {
            PolicyContract contract = invocation.getArgument(0);
            contract.setId(501L);
            return contract;
        });
        when(policyPeriodRepository.saveAndFlush(any(PolicyPeriod.class))).thenAnswer(invocation -> {
            PolicyPeriod period = invocation.getArgument(0);
            period.setId(601L);
            return period;
        });
        when(cacheManager.getCache(RedisCacheConfig.POLICY_CONTRACTS_CACHE)).thenReturn(cache);
        when(cacheManager.getCache(RedisCacheConfig.POLICY_CONTRACT_DETAILS_CACHE)).thenReturn(cache);

        PolicyContract contract = policyLifecycleService.createInitialPolicyForPurchase(purchase, confirmedAt);

        assertThat(contract).isNotNull();
        assertThat(contract.getPolicyNumber()).isEqualTo("POL-42-101");
        assertThat(contract.getStatus()).isEqualTo("ACTIVE");
        assertThat(contract.getCurrentPolicyPeriod()).isNotNull();
        assertThat(contract.getCurrentPolicyPeriod().getRenewalSequence()).isZero();
        assertThat(contract.getCurrentPolicyPeriod().getEffectiveDate()).isEqualTo(confirmedAt);
        assertThat(purchase.getPolicyContract()).isEqualTo(contract);
        assertThat(purchase.getTargetPolicyPeriod()).isEqualTo(contract.getCurrentPolicyPeriod());
    }

    @Test
    void invalidateCustomerPolicyCaches_evictsPolicyReadEntries() {
        when(cacheManager.getCache(RedisCacheConfig.POLICY_CONTRACTS_CACHE)).thenReturn(cache);
        when(cacheManager.getCache(RedisCacheConfig.POLICY_CONTRACT_DETAILS_CACHE)).thenReturn(cache);

        policyLifecycleService.invalidateCustomerPolicyCaches(42L, 501L);

        verify(cache).evictIfPresent("contract:customer:42:policies");
        verify(cache).evictIfPresent("contract:customer:42:policy:501");
    }

    @Test
    void cancelPolicy_updatesContractAndCurrentPeriodStatus() {
        Product product = Product.builder().id(11L).name("Auto").status("ACTIVE").build();
        Plan plan = Plan.builder().id(22L).product(product).name("Comprehensive").status("ACTIVE").annualPremiumCents(50000L).currency("INR").coverageLimitCents(1000000L).deductibleCents(25000L).build();
        PolicyPeriod period = PolicyPeriod.builder()
                .id(601L)
                .planId(plan.getId())
                .renewalSequence(0)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        PolicyContract contract = PolicyContract.builder()
                .id(501L)
                .customerId(42L)
                .productId(product.getId())
                .policyNumber("POL-42-101")
                .status("ACTIVE")
                .currentPolicyPeriod(period)
                .build();
        Instant cancelledAt = Instant.parse("2025-06-15T00:00:00Z");

        when(policyContractRepository.findByIdAndCustomerId(501L, 42L)).thenReturn(Optional.of(contract));
        when(policyPeriodRepository.save(any(PolicyPeriod.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(policyContractRepository.save(any(PolicyContract.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cacheManager.getCache(RedisCacheConfig.POLICY_CONTRACTS_CACHE)).thenReturn(cache);
        when(cacheManager.getCache(RedisCacheConfig.POLICY_CONTRACT_DETAILS_CACHE)).thenReturn(cache);

        PolicyContract cancelled = policyLifecycleService.cancelPolicy(501L, 42L, cancelledAt);

        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getCurrentPolicyPeriod().getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getCurrentPolicyPeriod().getCancelledAt()).isEqualTo(cancelledAt);
    }

    @Test
    void cancelPolicy_throwsResourceNotFound_whenPolicyDoesNotExist() {
        when(policyContractRepository.findByIdAndCustomerId(501L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policyLifecycleService.cancelPolicy(501L, 42L, Instant.now()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Policy not found");
    }

    @Test
    void cancelPolicy_throwsBadRequest_whenPolicyAlreadyCancelled() {
        PolicyContract contract = PolicyContract.builder()
                .id(501L)
                .customerId(42L)
                .productId(11L)
                .policyNumber("POL-42-101")
                .status("CANCELLED")
                .build();

        when(policyContractRepository.findByIdAndCustomerId(501L, 42L)).thenReturn(Optional.of(contract));

        PolicyContract result = policyLifecycleService.cancelPolicy(501L, 42L, Instant.now());

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelPolicy_throwsBadRequest_whenPolicyNotActive() {
        PolicyContract contract = PolicyContract.builder()
                .id(501L)
                .customerId(42L)
                .productId(11L)
                .policyNumber("POL-42-101")
                .status("EXPIRED")
                .build();

        when(policyContractRepository.findByIdAndCustomerId(501L, 42L)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> policyLifecycleService.cancelPolicy(501L, 42L, Instant.now()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot cancel policy in status");
    }

    @Test
    void cancelPolicy_throwsBadRequest_whenPolicyIdIsNull() {
        assertThatThrownBy(() -> policyLifecycleService.cancelPolicy(null, 42L, Instant.now()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Policy id is required");
    }

    @Test
    void cancelPolicy_throwsBadRequest_whenCustomerIdIsNull() {
        assertThatThrownBy(() -> policyLifecycleService.cancelPolicy(501L, null, Instant.now()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Customer id is required");
    }

    @Test
    void cancelPolicy_handlesNullCurrentPeriod() {
        PolicyContract contract = PolicyContract.builder()
                .id(501L)
                .customerId(42L)
                .productId(11L)
                .policyNumber("POL-42-101")
                .status("ACTIVE")
                .currentPolicyPeriod(null)
                .build();

        when(policyContractRepository.findByIdAndCustomerId(501L, 42L)).thenReturn(Optional.of(contract));
        when(policyContractRepository.save(any(PolicyContract.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cacheManager.getCache(RedisCacheConfig.POLICY_CONTRACTS_CACHE)).thenReturn(cache);
        when(cacheManager.getCache(RedisCacheConfig.POLICY_CONTRACT_DETAILS_CACHE)).thenReturn(cache);

        PolicyContract cancelled = policyLifecycleService.cancelPolicy(501L, 42L, Instant.now());

        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getCurrentPolicyPeriod()).isNull();
    }

    // Phase 21: Expiration/Lifecycle Completion Tests

    @Test
    void isPeriodActiveAt_returnsTrue_whenAsOfWithinPeriodWindow() {
        PolicyPeriod period = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        boolean isActive = policyLifecycleService.isPeriodActiveAt(period, Instant.parse("2025-06-15T00:00:00Z"));

        assertThat(isActive).isTrue();
    }

    @Test
    void isPeriodActiveAt_returnsTrue_whenAsOfExactlyEffectiveDate() {
        PolicyPeriod period = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        boolean isActive = policyLifecycleService.isPeriodActiveAt(period, Instant.parse("2025-01-01T00:00:00Z"));

        assertThat(isActive).isTrue();
    }

    @Test
    void isPeriodActiveAt_returnsTrue_whenAsOfJustBeforeExpirationDate() {
        PolicyPeriod period = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        boolean isActive = policyLifecycleService.isPeriodActiveAt(period, Instant.parse("2025-12-31T23:59:59Z"));

        assertThat(isActive).isTrue();
    }

    @Test
    void isPeriodActiveAt_returnsFalse_whenAsOfExactlyExpirationDate() {
        PolicyPeriod period = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        boolean isActive = policyLifecycleService.isPeriodActiveAt(period, Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(isActive).isFalse();
    }

    @Test
    void isPeriodActiveAt_returnsFalse_whenAsOfAfterExpirationDate() {
        PolicyPeriod period = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        boolean isActive = policyLifecycleService.isPeriodActiveAt(period, Instant.parse("2026-01-01T00:00:01Z"));

        assertThat(isActive).isFalse();
    }

    @Test
    void isPeriodActiveAt_returnsFalse_whenAsOfBeforeEffectiveDate() {
        PolicyPeriod period = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        boolean isActive = policyLifecycleService.isPeriodActiveAt(period, Instant.parse("2024-12-31T23:59:59Z"));

        assertThat(isActive).isFalse();
    }

    @Test
    void isPeriodActiveAt_returnsFalse_whenPeriodIsNull() {
        boolean isActive = policyLifecycleService.isPeriodActiveAt(null, Instant.now());

        assertThat(isActive).isFalse();
    }

    @Test
    void isPeriodActiveAt_defaultsToNow_whenAsOfIsNull() {
        PolicyPeriod period = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.now().minusSeconds(3600))
                .expirationDate(Instant.now().plusSeconds(3600))
                .build();

        boolean isActive = policyLifecycleService.isPeriodActiveAt(period, null);

        assertThat(isActive).isTrue();
    }

    @Test
    void isPeriodExpired_returnsTrue_whenAsOfAtExpirationDate() {
        PolicyPeriod period = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        boolean isExpired = policyLifecycleService.isPeriodExpired(period, Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(isExpired).isTrue();
    }

    @Test
    void isPeriodExpired_returnsTrue_whenAsOfAfterExpirationDate() {
        PolicyPeriod period = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        boolean isExpired = policyLifecycleService.isPeriodExpired(period, Instant.parse("2026-01-01T00:00:01Z"));

        assertThat(isExpired).isTrue();
    }

    @Test
    void isPeriodExpired_returnsFalse_whenAsOfBeforeExpirationDate() {
        PolicyPeriod period = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        boolean isExpired = policyLifecycleService.isPeriodExpired(period, Instant.parse("2025-12-31T23:59:59Z"));

        assertThat(isExpired).isFalse();
    }

    @Test
    void isPeriodExpired_returnsFalse_whenPeriodIsNull() {
        boolean isExpired = policyLifecycleService.isPeriodExpired(null, Instant.now());

        assertThat(isExpired).isFalse();
    }

    @Test
    void isCurrentPolicyPeriodActive_returnsTrue_whenCurrentPeriodIsActive() {
        PolicyPeriod currentPeriod = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.now().minusSeconds(3600))
                .expirationDate(Instant.now().plusSeconds(3600))
                .build();
        PolicyContract contract = PolicyContract.builder()
                .id(501L)
                .status("ACTIVE")
                .currentPolicyPeriod(currentPeriod)
                .build();

        boolean isActive = policyLifecycleService.isCurrentPolicyPeriodActive(contract, Instant.now());

        assertThat(isActive).isTrue();
    }

    @Test
    void isCurrentPolicyPeriodActive_returnsFalse_whenCurrentPeriodExpired() {
        PolicyPeriod currentPeriod = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        PolicyContract contract = PolicyContract.builder()
                .id(501L)
                .status("ACTIVE")
                .currentPolicyPeriod(currentPeriod)
                .build();

        boolean isActive = policyLifecycleService.isCurrentPolicyPeriodActive(contract, Instant.parse("2026-01-01T00:00:01Z"));

        assertThat(isActive).isFalse();
    }

    @Test
    void isCurrentPolicyPeriodActive_returnsFalse_whenContractIsNull() {
        boolean isActive = policyLifecycleService.isCurrentPolicyPeriodActive(null, Instant.now());

        assertThat(isActive).isFalse();
    }

    @Test
    void isCurrentPolicyPeriodActive_returnsFalse_whenCurrentPeriodIsNull() {
        PolicyContract contract = PolicyContract.builder()
                .id(501L)
                .status("ACTIVE")
                .currentPolicyPeriod(null)
                .build();

        boolean isActive = policyLifecycleService.isCurrentPolicyPeriodActive(contract, Instant.now());

        assertThat(isActive).isFalse();
    }

    // Phase 21: Renewal + Expiration Interaction Tests

    @Test
    void renewalInteraction_pendingRenewalDoesNotProvideCoverage_beforePayment() {
        PolicyPeriod currentPeriod = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        PolicyPeriod pendingRenewalPeriod = PolicyPeriod.builder()
                .id(602L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2026-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2027-01-01T00:00:00Z"))
                .build();

        boolean currentActive = policyLifecycleService.isPeriodActiveAt(currentPeriod, Instant.parse("2025-12-31T23:59:59Z"));
        boolean renewalActive = policyLifecycleService.isPeriodActiveAt(pendingRenewalPeriod, Instant.parse("2025-12-31T23:59:59Z"));

        assertThat(currentActive).isTrue();
        assertThat(renewalActive).isFalse();
    }

    @Test
    void renewalInteraction_failedRenewalDoesNotActivateCoverage() {
        PolicyPeriod currentPeriod = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        boolean currentActiveAtExpiration = policyLifecycleService.isPeriodActiveAt(currentPeriod, Instant.parse("2026-01-01T00:00:00Z"));
        boolean currentExpired = policyLifecycleService.isPeriodExpired(currentPeriod, Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(currentActiveAtExpiration).isFalse();
        assertThat(currentExpired).isTrue();
    }

    @Test
    void renewalInteraction_successfulRenewalActivatesReplacementPeriod() {
        PolicyPeriod oldPeriod = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        PolicyPeriod newPeriod = PolicyPeriod.builder()
                .id(602L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2026-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2027-01-01T00:00:00Z"))
                .build();
        PolicyContract contract = PolicyContract.builder()
                .id(501L)
                .status("ACTIVE")
                .currentPolicyPeriod(newPeriod)
                .build();

        boolean oldPeriodExpired = policyLifecycleService.isPeriodExpired(oldPeriod, Instant.parse("2026-01-01T00:00:00Z"));
        boolean newPeriodActive = policyLifecycleService.isPeriodActiveAt(newPeriod, Instant.parse("2026-01-01T00:00:00Z"));
        boolean currentPeriodActive = policyLifecycleService.isCurrentPolicyPeriodActive(contract, Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(oldPeriodExpired).isTrue();
        assertThat(newPeriodActive).isTrue();
        assertThat(currentPeriodActive).isTrue();
    }

    @Test
    void renewalInteraction_adjacentPeriodsNoGapNoOverlap() {
        PolicyPeriod period1 = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        PolicyPeriod period2 = PolicyPeriod.builder()
                .id(602L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2026-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2027-01-01T00:00:00Z"))
                .build();

        boolean period1ActiveBeforeBoundary = policyLifecycleService.isPeriodActiveAt(period1, Instant.parse("2025-12-31T23:59:59Z"));
        boolean period1ActiveAtBoundary = policyLifecycleService.isPeriodActiveAt(period1, Instant.parse("2026-01-01T00:00:00Z"));
        boolean period2ActiveAtBoundary = policyLifecycleService.isPeriodActiveAt(period2, Instant.parse("2026-01-01T00:00:00Z"));
        boolean period2ActiveAfterBoundary = policyLifecycleService.isPeriodActiveAt(period2, Instant.parse("2026-01-01T00:00:01Z"));

        assertThat(period1ActiveBeforeBoundary).isTrue();
        assertThat(period1ActiveAtBoundary).isFalse();
        assertThat(period2ActiveAtBoundary).isTrue();
        assertThat(period2ActiveAfterBoundary).isTrue();
    }

    @Test
    void renewalInteraction_expiredPeriodWithNoRenewalLeavesContractWithoutActiveCoverage() {
        PolicyPeriod expiredPeriod = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        PolicyContract contract = PolicyContract.builder()
                .id(501L)
                .status("ACTIVE")
                .currentPolicyPeriod(expiredPeriod)
                .build();

        boolean periodExpired = policyLifecycleService.isPeriodExpired(expiredPeriod, Instant.parse("2026-01-01T00:00:01Z"));
        boolean periodActive = policyLifecycleService.isPeriodActiveAt(expiredPeriod, Instant.parse("2026-01-01T00:00:01Z"));
        boolean currentPeriodActive = policyLifecycleService.isCurrentPolicyPeriodActive(contract, Instant.parse("2026-01-01T00:00:01Z"));

        assertThat(periodExpired).isTrue();
        assertThat(periodActive).isFalse();
        assertThat(currentPeriodActive).isFalse();
    }

    @Test
    void renewalInteraction_cancelledContractHistoricalPeriodsRemainImmutable() {
        PolicyPeriod historicalPeriod = PolicyPeriod.builder()
                .id(601L)
                .status("CANCELLED")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .cancelledAt(Instant.parse("2025-06-15T00:00:00Z"))
                .build();

        boolean periodExpired = policyLifecycleService.isPeriodExpired(historicalPeriod, Instant.parse("2026-01-01T00:00:00Z"));
        boolean periodActiveAtCancellationTime = policyLifecycleService.isPeriodActiveAt(historicalPeriod, Instant.parse("2025-06-15T00:00:00Z"));
        boolean periodActiveAfterCancellation = policyLifecycleService.isPeriodActiveAt(historicalPeriod, Instant.parse("2025-06-15T00:00:01Z"));

        assertThat(periodExpired).isTrue();
        assertThat(periodActiveAtCancellationTime).isTrue();
        assertThat(periodActiveAfterCancellation).isTrue();
    }

    @Test
    void renewalInteraction_historicalPeriodAsOfQueryReturnsCorrectStatus() {
        PolicyPeriod historicalPeriod = PolicyPeriod.builder()
                .id(601L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        boolean wasActiveDuringValidity = policyLifecycleService.isPeriodActiveAt(historicalPeriod, Instant.parse("2025-06-15T00:00:00Z"));
        boolean isNotActiveAfterExpiration = policyLifecycleService.isPeriodActiveAt(historicalPeriod, Instant.parse("2026-01-01T00:00:01Z"));

        assertThat(wasActiveDuringValidity).isTrue();
        assertThat(isNotActiveAfterExpiration).isFalse();
    }

    @Test
    void initiateRenewal_createsRenewalPurchaseForEligiblePolicy() {
        Product product = Product.builder().id(11L).name("Auto").status("ACTIVE").build();
        Plan plan = Plan.builder().id(22L).product(product).name("Comprehensive").status("ACTIVE").annualPremiumCents(50000L).currency("INR").coverageLimitCents(1000000L).deductibleCents(25000L).build();
        PolicyPeriod currentPeriod = PolicyPeriod.builder()
                .id(601L)
                .planId(plan.getId())
                .renewalSequence(0)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        PolicyContract contract = PolicyContract.builder()
                .id(501L)
                .customerId(42L)
                .productId(product.getId())
                .policyNumber("POL-42-101")
                .status("ACTIVE")
                .currentPolicyPeriod(currentPeriod)
                .build();

        when(policyContractRepository.findByIdAndCustomerId(501L, 42L)).thenReturn(Optional.of(contract));
        when(purchaseRepository.findBySourcePolicyPeriodIdAndPurchaseTypeAndStatusIn(601L, PurchaseType.RENEWAL, Set.of(PurchaseStatus.PENDING_PAYMENT, PurchaseStatus.PAYMENT_PROCESSING))).thenReturn(Optional.empty());
        when(planRepository.findById(22L)).thenReturn(Optional.of(plan));
        when(purchaseRepository.saveAndFlush(any(Purchase.class))).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            purchase.setId(201L);
            return purchase;
        });
        when(stripePaymentGateway.isEnabled()).thenReturn(false);

        RenewalInitiationResponse response = policyLifecycleService.initiateRenewal(501L, 42L, "test-idempotency-key");

        assertThat(response).isNotNull();
        assertThat(response.purchaseId()).isEqualTo(201L);
        assertThat(response.policyContractId()).isEqualTo(501L);
        assertThat(response.sourcePolicyPeriodId()).isEqualTo(601L);
        assertThat(response.status()).isEqualTo(PurchaseStatus.PENDING_PAYMENT.name());
        assertThat(response.amountCents()).isEqualTo(50000L);
        assertThat(response.currency()).isEqualTo("INR");
        assertThat(response.idempotencyKey()).isEqualTo("test-idempotency-key");
    }

    @Test
    void initiateRenewal_throwsResourceNotFound_whenPolicyDoesNotExist() {
        when(policyContractRepository.findByIdAndCustomerId(501L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policyLifecycleService.initiateRenewal(501L, 42L, "test-key"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Policy contract not found");
    }

    @Test
    void initiateRenewal_throwsBadRequest_whenPolicyCancelled() {
        PolicyPeriod currentPeriod = PolicyPeriod.builder()
                .id(601L)
                .planId(22L)
                .renewalSequence(0)
                .status("CANCELLED")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        PolicyContract contract = PolicyContract.builder()
                .id(501L)
                .customerId(42L)
                .productId(11L)
                .policyNumber("POL-42-101")
                .status("CANCELLED")
                .currentPolicyPeriod(currentPeriod)
                .build();

        when(policyContractRepository.findByIdAndCustomerId(501L, 42L)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> policyLifecycleService.initiateRenewal(501L, 42L, "test-key"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot renew a cancelled policy");
    }

    @Test
    void initiateRenewal_throwsBadRequest_whenNoCurrentPeriod() {
        PolicyContract contract = PolicyContract.builder()
                .id(501L)
                .customerId(42L)
                .productId(11L)
                .policyNumber("POL-42-101")
                .status("ACTIVE")
                .currentPolicyPeriod(null)
                .build();

        when(policyContractRepository.findByIdAndCustomerId(501L, 42L)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> policyLifecycleService.initiateRenewal(501L, 42L, "test-key"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Policy has no current period to renew");
    }

    @Test
    void initiateRenewal_returnsExistingPurchase_whenRenewalAlreadyInProgress() {
        Product product = Product.builder().id(11L).name("Auto").status("ACTIVE").build();
        Plan plan = Plan.builder().id(22L).product(product).name("Comprehensive").status("ACTIVE").annualPremiumCents(50000L).currency("INR").coverageLimitCents(1000000L).deductibleCents(25000L).build();
        PolicyPeriod currentPeriod = PolicyPeriod.builder()
                .id(601L)
                .planId(plan.getId())
                .renewalSequence(0)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        PolicyContract contract = PolicyContract.builder()
                .id(501L)
                .customerId(42L)
                .productId(product.getId())
                .policyNumber("POL-42-101")
                .status("ACTIVE")
                .currentPolicyPeriod(currentPeriod)
                .build();
        Purchase existingRenewal = Purchase.builder()
                .id(201L)
                .customerId(42L)
                .purchaseType(PurchaseType.RENEWAL)
                .policyContract(contract)
                .sourcePolicyPeriod(currentPeriod)
                .status(PurchaseStatus.PENDING_PAYMENT)
                .amountCents(50000L)
                .currency("INR")
                .idempotencyKey("existing-key")
                .initiatedAt(Instant.now())
                .build();

        when(policyContractRepository.findByIdAndCustomerId(501L, 42L)).thenReturn(Optional.of(contract));
        when(purchaseRepository.findBySourcePolicyPeriodIdAndPurchaseTypeAndStatusIn(601L, PurchaseType.RENEWAL, Set.of(PurchaseStatus.PENDING_PAYMENT, PurchaseStatus.PAYMENT_PROCESSING))).thenReturn(Optional.of(existingRenewal));
        when(stripePaymentGateway.isEnabled()).thenReturn(false);

        RenewalInitiationResponse response = policyLifecycleService.initiateRenewal(501L, 42L, "new-key");

        assertThat(response.purchaseId()).isEqualTo(201L);
        assertThat(response.idempotencyKey()).isEqualTo("existing-key");
    }

    @Test
    void activateRenewal_createsNewPolicyPeriodAndUpdatesContract() {
        Product product = Product.builder().id(11L).name("Auto").status("ACTIVE").build();
        Plan plan = Plan.builder().id(22L).product(product).name("Comprehensive").status("ACTIVE").annualPremiumCents(50000L).currency("INR").coverageLimitCents(1000000L).deductibleCents(25000L).build();
        PolicyPeriod sourcePeriod = PolicyPeriod.builder()
                .id(601L)
                .planId(plan.getId())
                .renewalSequence(0)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        PolicyContract contract = PolicyContract.builder()
                .id(501L)
                .customerId(42L)
                .productId(product.getId())
                .policyNumber("POL-42-101")
                .status("ACTIVE")
                .currentPolicyPeriod(sourcePeriod)
                .build();
        Purchase renewalPurchase = Purchase.builder()
                .id(201L)
                .customerId(42L)
                .purchaseType(PurchaseType.RENEWAL)
                .policyContract(contract)
                .sourcePolicyPeriod(sourcePeriod)
                .status(PurchaseStatus.PAID)
                .amountCents(50000L)
                .currency("INR")
                .idempotencyKey("test-key")
                .initiatedAt(Instant.now())
                .paidAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        Instant paymentConfirmedAt = Instant.parse("2026-01-01T00:00:00Z");

        when(policyPeriodRepository.saveAndFlush(any(PolicyPeriod.class))).thenAnswer(invocation -> {
            PolicyPeriod period = invocation.getArgument(0);
            period.setId(602L);
            return period;
        });
        when(policyContractRepository.save(any(PolicyContract.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cacheManager.getCache(RedisCacheConfig.POLICY_CONTRACTS_CACHE)).thenReturn(cache);
        when(cacheManager.getCache(RedisCacheConfig.POLICY_CONTRACT_DETAILS_CACHE)).thenReturn(cache);

        policyLifecycleService.activateRenewal(renewalPurchase, paymentConfirmedAt);

        assertThat(renewalPurchase.getTargetPolicyPeriod()).isNotNull();
        assertThat(renewalPurchase.getTargetPolicyPeriod().getId()).isEqualTo(602L);
        assertThat(renewalPurchase.getTargetPolicyPeriod().getRenewalSequence()).isEqualTo(1);
        assertThat(renewalPurchase.getTargetPolicyPeriod().getEffectiveDate()).isEqualTo(sourcePeriod.getExpirationDate());
        assertThat(renewalPurchase.getTargetPolicyPeriod().getExpirationDate()).isEqualTo(sourcePeriod.getExpirationDate().plus(365, java.time.temporal.ChronoUnit.DAYS));
        assertThat(renewalPurchase.getTargetPolicyPeriod().getPreviousPolicyPeriod()).isEqualTo(sourcePeriod);
        assertThat(contract.getCurrentPolicyPeriod()).isEqualTo(renewalPurchase.getTargetPolicyPeriod());
    }

    @Test
    void activateRenewal_throwsBadRequest_whenPurchaseNotRenewal() {
        Purchase purchase = Purchase.builder()
                .id(201L)
                .purchaseType(PurchaseType.NEW_POLICY)
                .status(PurchaseStatus.PAID)
                .build();

        assertThatThrownBy(() -> policyLifecycleService.activateRenewal(purchase, Instant.now()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Purchase is not a renewal purchase");
    }

    @Test
    void activateRenewal_throwsBadRequest_whenPurchaseNotPaid() {
        Purchase purchase = Purchase.builder()
                .id(201L)
                .purchaseType(PurchaseType.RENEWAL)
                .status(PurchaseStatus.PENDING_PAYMENT)
                .build();

        assertThatThrownBy(() -> policyLifecycleService.activateRenewal(purchase, Instant.now()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Purchase must be in PAID status to activate renewal");
    }

    @Test
    void activateRenewal_isIdempotent_whenAlreadyActivated() {
        Product product = Product.builder().id(11L).name("Auto").status("ACTIVE").build();
        Plan plan = Plan.builder().id(22L).product(product).name("Comprehensive").status("ACTIVE").annualPremiumCents(50000L).currency("INR").coverageLimitCents(1000000L).deductibleCents(25000L).build();
        PolicyPeriod sourcePeriod = PolicyPeriod.builder()
                .id(601L)
                .planId(plan.getId())
                .renewalSequence(0)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        PolicyPeriod targetPeriod = PolicyPeriod.builder()
                .id(602L)
                .planId(plan.getId())
                .renewalSequence(1)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2026-01-01T00:00:00Z"))
                .expirationDate(Instant.parse("2027-01-01T00:00:00Z"))
                .previousPolicyPeriod(sourcePeriod)
                .build();
        PolicyContract contract = PolicyContract.builder()
                .id(501L)
                .customerId(42L)
                .productId(product.getId())
                .policyNumber("POL-42-101")
                .status("ACTIVE")
                .currentPolicyPeriod(targetPeriod)
                .build();
        Purchase renewalPurchase = Purchase.builder()
                .id(201L)
                .customerId(42L)
                .purchaseType(PurchaseType.RENEWAL)
                .policyContract(contract)
                .sourcePolicyPeriod(sourcePeriod)
                .targetPolicyPeriod(targetPeriod)
                .status(PurchaseStatus.PAID)
                .amountCents(50000L)
                .currency("INR")
                .idempotencyKey("test-key")
                .initiatedAt(Instant.now())
                .paidAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        policyLifecycleService.activateRenewal(renewalPurchase, Instant.now());

        assertThat(renewalPurchase.getTargetPolicyPeriod()).isEqualTo(targetPeriod);
    }
}
