package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.dto.EndorseRequestDto;
import com.claimassist.platform.policy_service.entity.Policy;
import com.claimassist.platform.policy_service.entity.LifecycleStatus;
import com.claimassist.platform.policy_service.entity.PolicyVersion;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.PolicyRepository;
import com.claimassist.platform.policy_service.repository.PolicyVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PolicyLifecycleServiceTest {

    private PolicyRepository policyRepository;
    private PolicyVersionRepository policyVersionRepository;
    private PlanRepository planRepository;
    private PolicyCreationService policyCreationService;
    private IdempotencyService idempotencyService;
    private ObjectMapper objectMapper;
    private org.springframework.cache.CacheManager cacheManager;
    private PolicyLifecycleService svc;

    @BeforeEach
    void setup() {
        policyRepository = mock(PolicyRepository.class);
        policyVersionRepository = mock(PolicyVersionRepository.class);
        planRepository = mock(PlanRepository.class);
        policyCreationService = mock(PolicyCreationService.class);
        idempotencyService = mock(IdempotencyService.class);
        objectMapper = new ObjectMapper();
        cacheManager = mock(org.springframework.cache.CacheManager.class);
        svc = new PolicyLifecycleService(policyRepository, policyVersionRepository, planRepository, policyCreationService, idempotencyService, objectMapper, cacheManager);
    }

    @Test
    void issue_success_when_payment_succeeded() {
        Policy p = Policy.builder().id(1L).status("PENDING_PAYMENT").stripePaymentIntentId("pi_123").build();
        when(policyRepository.findById(1L)).thenReturn(Optional.of(p));
        when(policyCreationService.verifyStripePaymentForPolicy(ArgumentMatchers.eq("pi_123"), ArgumentMatchers.any())).thenReturn(true);
        when(policyRepository.save(ArgumentMatchers.any(Policy.class))).thenAnswer(i -> i.getArgument(0));

        Policy out = svc.issue(1L, null);
        assertThat(out.getStatus()).isEqualTo("ACTIVE");
        verify(policyCreationService, times(1)).verifyStripePaymentForPolicy(ArgumentMatchers.eq("pi_123"), ArgumentMatchers.any());
    }

    @Test
    void issue_fails_when_payment_incomplete() {
        Policy p = Policy.builder().id(2L).status("PENDING_PAYMENT").stripePaymentIntentId("pi_999").build();
        when(policyRepository.findById(2L)).thenReturn(Optional.of(p));
        when(policyCreationService.verifyStripePaymentForPolicy(ArgumentMatchers.eq("pi_999"), ArgumentMatchers.any())).thenReturn(false);

        assertThatThrownBy(() -> svc.issue(2L, null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void issue_rejects_when_payment_metadata_mismatch() {
        // Setup a policy pending payment
        Policy p = Policy.builder().id(9L).status("PENDING_PAYMENT").stripePaymentIntentId("pi_policy").build();
        when(policyRepository.findById(9L)).thenReturn(Optional.of(p));

        // Supplied PaymentIntent id is different (or metadata inside it does not match the policy)
        String suppliedPi = "pi_mismatch";

        // Mock server-side verification to return false to simulate metadata mismatch
        when(policyCreationService.verifyStripePaymentForPolicy(ArgumentMatchers.eq(suppliedPi), ArgumentMatchers.any())).thenReturn(false);

        // Expect BadRequest and ensure no policy save occurs
        assertThatThrownBy(() -> svc.issue(9L, suppliedPi)).isInstanceOf(BadRequestException.class);
        verify(policyCreationService, times(1)).verifyStripePaymentForPolicy(ArgumentMatchers.eq(suppliedPi), ArgumentMatchers.any());
        verify(policyRepository, never()).save(ArgumentMatchers.any(Policy.class));
    }

    @Test
    void endorse_creates_new_version_and_updates_plan() {
        com.claimassist.platform.policy_service.entity.Product prod = com.claimassist.platform.policy_service.entity.Product.builder().id(99L).code("AUTO").name("Auto").build();
        Plan plan = Plan.builder().id(11L).code("BASIC").product(prod).build();
        Policy p = Policy.builder().id(3L).status("ACTIVE").coveragePlan(plan).effectiveDate(Instant.now()).build();
        when(policyRepository.findById(3L)).thenReturn(Optional.of(p));
        when(policyRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(p));
        PolicyVersion latest = PolicyVersion.builder().id(5L).versionNumber(1).plan(p.getCoveragePlan()).premiumCents(1000L).deductibleCents(200L).coverageLimitCents(50000L).effectiveFrom(p.getEffectiveDate()).build();
        when(policyVersionRepository.findByPolicyIdOrderByVersionNumber(3L)).thenReturn(List.of(latest));
        Plan newPlan = Plan.builder().id(12L).code("PLUS").product(prod).build();
        when(planRepository.findByCodeAndProductId(eq("PLUS"), anyLong())).thenReturn(Optional.of(newPlan));
        // idempotency wrapper should be invoked; simulate direct execution
        when(idempotencyService.execute(anyString(), anyString(), any(), anyString(), eq(java.util.Map.class), any())).thenAnswer(invocation -> {
            java.util.concurrent.Callable<?> supplier = () -> {
                // invoke the real supplier by extracting from args
                java.util.function.Supplier<?> s = invocation.getArgument(5);
                return s.get();
            };
            return ((java.util.function.Supplier<?>) invocation.getArgument(5)).get();
        });

        EndorseRequestDto req = new EndorseRequestDto();
        req.planCode = "PLUS";
        req.deductibleCents = 300L;

        when(policyRepository.save(any(Policy.class))).thenAnswer(i -> i.getArgument(0));
        when(policyVersionRepository.save(any(PolicyVersion.class))).thenAnswer(i -> i.getArgument(0));

        Policy out = svc.endorse(3L, req, "idem-key-1");
        assertThat(out.getCoveragePlan().getCode()).isEqualTo("PLUS");
        verify(policyVersionRepository, times(1)).save(any(PolicyVersion.class));
    }
}
