package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.policy_service.entity.Coverage;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.repository.CoverageRepository;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.ProductRepository;
import com.claimassist.platform.policy_service.repository.PolicyRepository;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PolicyCreationServiceIdempotencyTest {

    ProductRepository productRepository = mock(ProductRepository.class);
    PlanRepository planRepository = mock(PlanRepository.class);
    CoverageRepository coverageRepository = mock(CoverageRepository.class);
    PolicyRepository policyRepository = mock(PolicyRepository.class);
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    com.claimassist.platform.policy_service.security.InternalRequestIdentity internalRequestIdentity = mock(com.claimassist.platform.policy_service.security.InternalRequestIdentity.class);

    PolicyCreationService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new PolicyCreationService(policyRepository, planRepository, productRepository, coverageRepository, idempotencyService, internalRequestIdentity);
        // Inject a mocked EntityManager because the service uses @PersistenceContext in production
        jakarta.persistence.EntityManager mockEm = mock(jakarta.persistence.EntityManager.class);
        jakarta.persistence.Query mockQuery = mock(jakarta.persistence.Query.class);
        when(mockQuery.getSingleResult()).thenReturn(1L);
        when(mockEm.createNativeQuery(anyString())).thenReturn(mockQuery);
        java.lang.reflect.Field emField = PolicyCreationService.class.getDeclaredField("entityManager");
        emField.setAccessible(true);
        emField.set(service, mockEm);

        // Mock InternalRequestIdentity to return a valid user ID
        when(internalRequestIdentity.resolveCallingUserId(anyString())).thenReturn(1L);
    }

    @Test
    void createPolicy_with_idempotency_replay_returns_cached_result_and_prevents_duplicate_payment_intent() throws Exception {
        Product product = new Product();
        product.setId(1L);
        product.setCode("P1");
        when(productRepository.findByCode(any())).thenReturn(Optional.of(product));

        Plan plan = new Plan();
        plan.setId(2L);
        plan.setCode("PL1");
        plan.setProduct(product);
        plan.setActive(true);
        plan.setCreatedAt(Instant.now().minusSeconds(3600));
        plan.setDeductibleCents(1000L);
        plan.setPremiumCents(2000L);
        when(planRepository.findByCodeAndProductId(any(), anyLong())).thenReturn(Optional.of(plan));

        Coverage coverage = new Coverage();
        coverage.setId(3L);
        coverage.setCode("C1");
        coverage.setDeductibleCents(1000L);
        coverage.setLimitCents(50000L);
        when(coverageRepository.findByCodeAndPlanId(any(), anyLong())).thenReturn(Optional.of(coverage));

        // Mock stripe PaymentIntent.create static
        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_test_123");
        when(mockIntent.getClientSecret()).thenReturn("cs_test_123");
        when(mockIntent.getAmount()).thenReturn(2000L);
        when(mockIntent.getCurrency()).thenReturn("usd");

        // Prepare a saved policy to return from repository
        com.claimassist.platform.policy_service.entity.Policy saved = com.claimassist.platform.policy_service.entity.Policy.builder().id(10L).policyNumber("POL-900-000001").build();
        when(policyRepository.save(any())).thenAnswer(invocation -> {
            com.claimassist.platform.policy_service.entity.Policy p = invocation.getArgument(0);
            p.setId(10L);
            return p;
        });
        when(policyRepository.findById(10L)).thenReturn(Optional.of(saved));

        // First idempotent execution: idempotencyService should call the supplier
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            Object last = args[args.length - 1];
            if (last instanceof java.util.function.Supplier) {
                java.util.function.Supplier<?> supplier = (java.util.function.Supplier<?>) last;
                return supplier.get();
            }
            return null;
        }).when(idempotencyService).execute(anyString(), anyString(), anyLong(), anyString(), any(), any());

        try (MockedStatic<com.stripe.model.PaymentIntent> mocked = Mockito.mockStatic(com.stripe.model.PaymentIntent.class)) {
            mocked.when(() -> com.stripe.model.PaymentIntent.create(any(PaymentIntentCreateParams.class), any()))
                    .thenReturn(mockIntent);

            // Provide a test stripe secret so service does not fail-closed in test
            System.setProperty("STRIPE_SECRET_KEY", "sk_test_local");

            PolicyCreationRequest req = new PolicyCreationRequest();
            req.setCustomerId(900L);
            req.setProductCode("P1");
            req.setPlanCode("PL1");
            req.setCoverageCode("C1");
            req.setEffectiveDate(Instant.now().plusSeconds(3600));

            var p1 = service.createPolicy(req, "900", "idem-unit-1");

// Now simulate replay: idempotencyService should return cached map without invoking supplier
when(idempotencyService.execute(anyString(), anyString(), anyLong(), anyString(), any(), any()))
        .thenReturn(java.util.Map.of("policyId", 10L, "policyNumber", "POL-900-000001"));

            var p2 = service.createPolicy(req, "900", "idem-unit-1");

            // Verify stripe called only once
            mocked.verify(() -> com.stripe.model.PaymentIntent.create(any(PaymentIntentCreateParams.class), any()), times(1));

            assertThat(p1.getId()).isEqualTo(10L);
            assertThat(p2.getId()).isEqualTo(10L);
        }
    }
}
