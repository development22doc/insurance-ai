package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.policy_service.entity.*;
import com.claimassist.platform.policy_service.repository.PolicyContractRepository;
import com.claimassist.platform.policy_service.repository.PolicyPeriodRepository;
import com.claimassist.platform.policy_service.repository.PurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class PolicyLifecycleActivationRecoveryTest {

    @Mock
    private PolicyContractRepository policyContractRepository;

    @Mock
    private PolicyPeriodRepository policyPeriodRepository;

    @Mock
    private PurchaseRepository purchaseRepository;

    @InjectMocks
    private PolicyLifecycleService policyLifecycleService;

    private Purchase purchase;
    private Plan plan;

    @BeforeEach
    void setUp() {
        plan = Plan.builder().id(5L).name("Plan").build();
        purchase = Purchase.builder().id(500L).customerId(42L).plan(plan).status(PurchaseStatus.PAYMENT_PROCESSING).initiatedAt(Instant.now()).build();
    }

    @Test
    void createInitialPolicyForPurchase_whenContractSaveCollision_andCompleteInvariant_exists_returnsExisting() {
        // Simulate save throwing duplicate-key
        when(policyContractRepository.saveAndFlush(any(PolicyContract.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        PolicyContract existing = PolicyContract.builder().id(100L).customerId(42L).policyNumber("POL-42-500").status("ACTIVE").build();
        PolicyPeriod period = PolicyPeriod.builder().id(200L).policyContract(existing).planId(5L).renewalSequence(0).status("ACTIVE").effectiveDate(Instant.now()).expirationDate(Instant.now().plusSeconds(1000)).build();
        existing.setCurrentPolicyPeriod(period);
        // First call returns empty (no existing at start), second call (after collision) returns existing
        when(policyContractRepository.findByCustomerIdAndPolicyNumber(42L, "POL-42-500")).thenReturn(Optional.empty(), Optional.of(existing));

        PolicyContract result = policyLifecycleService.createInitialPolicyForPurchase(purchase, Instant.now());
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    void createInitialPolicyForPurchase_whenContractSaveCollision_andInvariantIncomplete_throws() {
        when(policyContractRepository.saveAndFlush(any(PolicyContract.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        PolicyContract existing = PolicyContract.builder().id(101L).customerId(42L).policyNumber("POL-42-500").status("ACTIVE").build();
        // Note: no currentPolicyPeriod
        // simulate initial absence then presence on retry
        when(policyContractRepository.findByCustomerIdAndPolicyNumber(42L, "POL-42-500")).thenReturn(Optional.empty(), Optional.of(existing));

        assertThatThrownBy(() -> policyLifecycleService.createInitialPolicyForPurchase(purchase, Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
