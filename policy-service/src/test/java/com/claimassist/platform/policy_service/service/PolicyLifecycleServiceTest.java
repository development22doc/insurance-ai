package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.config.RedisCacheConfig;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.PolicyContract;
import com.claimassist.platform.policy_service.entity.PolicyPeriod;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.entity.Purchase;
import com.claimassist.platform.policy_service.repository.PolicyContractRepository;
import com.claimassist.platform.policy_service.repository.PolicyPeriodRepository;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

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
        when(cacheManager.getCache(RedisCacheConfig.CUSTOMER_POLICIES_CACHE)).thenReturn(cache);
        when(cacheManager.getCache(RedisCacheConfig.CUSTOMER_POLICY_DETAILS_CACHE)).thenReturn(cache);
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
        when(cacheManager.getCache(RedisCacheConfig.CUSTOMER_POLICIES_CACHE)).thenReturn(cache);
        when(cacheManager.getCache(RedisCacheConfig.CUSTOMER_POLICY_DETAILS_CACHE)).thenReturn(cache);
        when(cacheManager.getCache(RedisCacheConfig.POLICY_CONTRACTS_CACHE)).thenReturn(cache);
        when(cacheManager.getCache(RedisCacheConfig.POLICY_CONTRACT_DETAILS_CACHE)).thenReturn(cache);

        policyLifecycleService.invalidateCustomerPolicyCaches(42L, 501L);

        verify(cache).evictIfPresent("customer:42:policies");
        verify(cache).evictIfPresent("customer:42:policy:501");
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
        when(cacheManager.getCache(RedisCacheConfig.CUSTOMER_POLICIES_CACHE)).thenReturn(cache);
        when(cacheManager.getCache(RedisCacheConfig.CUSTOMER_POLICY_DETAILS_CACHE)).thenReturn(cache);
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
        when(cacheManager.getCache(RedisCacheConfig.CUSTOMER_POLICIES_CACHE)).thenReturn(cache);
        when(cacheManager.getCache(RedisCacheConfig.CUSTOMER_POLICY_DETAILS_CACHE)).thenReturn(cache);
        when(cacheManager.getCache(RedisCacheConfig.POLICY_CONTRACTS_CACHE)).thenReturn(cache);
        when(cacheManager.getCache(RedisCacheConfig.POLICY_CONTRACT_DETAILS_CACHE)).thenReturn(cache);

        PolicyContract cancelled = policyLifecycleService.cancelPolicy(501L, 42L, Instant.now());

        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getCurrentPolicyPeriod()).isNull();
    }
}
