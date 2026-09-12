package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.dto.EndorseRequestDto;
import com.claimassist.platform.policy_service.dto.RenewRequestDto;
import com.claimassist.platform.policy_service.dto.ReinstateRequestDto;
import com.claimassist.platform.policy_service.entity.Policy;
import com.claimassist.platform.policy_service.entity.PolicyVersion;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.repository.PolicyRepository;
import com.claimassist.platform.policy_service.repository.PolicyVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

class PolicyLifecycleConcurrencyTest {

    private PolicyRepository policyRepository;

    private PolicyVersionRepository policyVersionRepository;

    private com.claimassist.platform.policy_service.repository.PlanRepository planRepository;

    private PolicyCreationService policyCreationService;

    private IdempotencyService idempotencyService;

    private org.springframework.cache.CacheManager cacheManager;

    private PolicyLifecycleService policyLifecycleService;

    private Policy policy;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        policyRepository = mock(PolicyRepository.class);
        policyVersionRepository = mock(PolicyVersionRepository.class);
        planRepository = mock(com.claimassist.platform.policy_service.repository.PlanRepository.class);
        policyCreationService = mock(PolicyCreationService.class);
        idempotencyService = mock(IdempotencyService.class);
        cacheManager = mock(org.springframework.cache.CacheManager.class);
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        // enable Java Time support for fingerprint serialization
        com.fasterxml.jackson.datatype.jsr310.JavaTimeModule jtm = new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule();
        om.registerModule(jtm);
        policyLifecycleService = new PolicyLifecycleService(policyRepository, policyVersionRepository, planRepository, policyCreationService, idempotencyService, om, cacheManager);

        policy = Policy.builder()
                .id(1L)
                .policyNumber("POL-1")
                .customerId(7L)
                .status("ACTIVE")
                .effectiveDate(Instant.parse("2025-01-01T00:00:00Z"))
                .build();
    }

    @Test
    void endorse_usesFindByIdForUpdate() {
        when(policyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(policy));
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyVersionRepository.findByPolicyIdOrderByVersionNumber(1L)).thenReturn(List.of());
        when(idempotencyService.execute(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<?> command = invocation.getArgument(5);
            return command.get();
        });

        EndorseRequestDto req = new EndorseRequestDto();
        req.planCode = null;

        policyLifecycleService.endorse(1L, req, "key-1");

        verify(policyRepository, times(1)).findByIdForUpdate(1L);
        verify(policyVersionRepository, times(1)).save(ArgumentMatchers.any(PolicyVersion.class));
    }

    @Test
    void renew_usesFindByIdForUpdate() {
        when(policyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(policy));
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyVersionRepository.findByPolicyIdOrderByVersionNumber(1L)).thenReturn(List.of());
        when(idempotencyService.execute(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<?> command = invocation.getArgument(5);
            return command.get();
        });

        RenewRequestDto req = new RenewRequestDto();
        req.effectiveFrom = Instant.parse("2026-01-01T00:00:00Z");

        policyLifecycleService.renew(1L, req, "key-2");

        verify(policyRepository, times(1)).findByIdForUpdate(1L);
        verify(policyVersionRepository, times(1)).save(ArgumentMatchers.any(PolicyVersion.class));
    }

    @Test
    void reinstate_usesFindByIdForUpdate() {
        // reinstate should start from CANCELLED/EXPIRED
        policy.setStatus("CANCELLED");
        when(policyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(policy));
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyVersionRepository.findByPolicyIdOrderByVersionNumber(1L)).thenReturn(List.of());
        when(policyCreationService.verifyStripePaymentForPolicy(ArgumentMatchers.eq("pi-1"), ArgumentMatchers.any())).thenReturn(true);

        ReinstateRequestDto req = new ReinstateRequestDto();
        req.stripePaymentIntentId = "pi-1";

        when(idempotencyService.execute(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<?> command = invocation.getArgument(5);
            return command.get();
        });

        // pass a currentUserId and an idempotency key for the payment-backed path
        policyLifecycleService.reinstate(1L, req, 7L, "key-1");

        verify(policyRepository, times(1)).findByIdForUpdate(1L);
        verify(policyVersionRepository, times(1)).save(ArgumentMatchers.any(PolicyVersion.class));
    }
}
