package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.policy_service.entity.Coverage;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.PolicyVersion;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PolicyCreationServiceStripeTest {

    ProductRepository productRepository = mock(ProductRepository.class);
    PlanRepository planRepository = mock(PlanRepository.class);
    CoverageRepository coverageRepository = mock(CoverageRepository.class);
    PolicyRepository policyRepository = mock(PolicyRepository.class);
    com.claimassist.platform.policy_service.service.IdempotencyService idempotencyService = mock(com.claimassist.platform.policy_service.service.IdempotencyService.class);

    PolicyCreationService service;
    jakarta.persistence.EntityManager mockEm;

    @BeforeEach
    void setUp() throws Exception {
        // make idempotencyService default to executing the supplier immediately
        org.mockito.stubbing.Answer<Object> execAnswer = invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(5);
            return supplier.get();
        };
        when(idempotencyService.execute(anyString(), anyString(), anyLong(), anyString(), any(), any())).thenAnswer(execAnswer);

        service = new PolicyCreationService(policyRepository, planRepository, productRepository, coverageRepository, idempotencyService);
        // Inject a mocked EntityManager because the service uses @PersistenceContext in production
        mockEm = mock(jakarta.persistence.EntityManager.class);
        jakarta.persistence.Query mockQuery = mock(jakarta.persistence.Query.class);
        when(mockQuery.getSingleResult()).thenReturn(1L);
        when(mockEm.createNativeQuery(anyString())).thenReturn(mockQuery);
        java.lang.reflect.Field emField = PolicyCreationService.class.getDeclaredField("entityManager");
        emField.setAccessible(true);
        emField.set(service, mockEm);
    }

    @Test
    void createPolicy_createsPaymentIntent_and_persists_paymentIntentId() throws Exception {
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

        try (MockedStatic<com.stripe.model.PaymentIntent> mocked = Mockito.mockStatic(com.stripe.model.PaymentIntent.class)) {
            mocked.when(() -> com.stripe.model.PaymentIntent.create(any(PaymentIntentCreateParams.class), any()))
                    .thenReturn(mockIntent);

        // Provide a test stripe secret so service does not fail-closed in test
        System.setProperty("STRIPE_SECRET_KEY", "sk_test_local");

        PolicyCreationRequest req = new PolicyCreationRequest();
            req.setCustomerId(42L);
            req.setProductCode("P1");
            req.setPlanCode("PL1");
            req.setCoverageCode("C1");
            req.setEffectiveDate(Instant.now().plusSeconds(3600));

            // Allow save to return the passed policy and assign an id
            when(policyRepository.save(any())).thenAnswer(invocation -> {
                com.claimassist.platform.policy_service.entity.Policy p = invocation.getArgument(0);
                if (p.getId() == null) p.setId(10L);
                return p;
            });
            // Ensure findById can locate the saved policy
            com.claimassist.platform.policy_service.entity.Policy found = com.claimassist.platform.policy_service.entity.Policy.builder().id(10L).policyNumber("POL-42-000001").stripePaymentIntentId("pi_test_123").status("PENDING_PAYMENT").build();
            when(policyRepository.findById(10L)).thenReturn(java.util.Optional.of(found));

            var policy = service.createPolicy(req, "42", "idem-1");
            assertThat(policy).isNotNull();
            assertThat(policy.getStripePaymentIntentId()).isEqualTo("pi_test_123");
            assertThat(policy.getStatus()).isEqualTo("PENDING_PAYMENT");

            mocked.verify(() -> com.stripe.model.PaymentIntent.create(any(PaymentIntentCreateParams.class), any()));
        }
    }

    @Test
    void createPolicy_doesNotBackfillPremiumFromDeductible() throws Exception {
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
        when(planRepository.findByCodeAndProductId(any(), anyLong())).thenReturn(Optional.of(plan));

        Coverage coverage = new Coverage();
        coverage.setId(3L);
        coverage.setCode("C1");
        coverage.setDeductibleCents(1000L);
        coverage.setLimitCents(50000L);
        when(coverageRepository.findByCodeAndPlanId(any(), anyLong())).thenReturn(Optional.of(coverage));

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_test_123");

        AtomicReference<PolicyVersion> persistedVersion = new AtomicReference<>();
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg instanceof PolicyVersion version) {
                persistedVersion.set(version);
            }
            return null;
        }).when(mockEm).persist(any());

        try (MockedStatic<com.stripe.model.PaymentIntent> mocked = Mockito.mockStatic(com.stripe.model.PaymentIntent.class)) {
            mocked.when(() -> com.stripe.model.PaymentIntent.create(any(PaymentIntentCreateParams.class), any()))
                    .thenReturn(mockIntent);

            System.setProperty("STRIPE_SECRET_KEY", "sk_test_local");

            PolicyCreationRequest req = new PolicyCreationRequest();
            req.setCustomerId(42L);
            req.setProductCode("P1");
            req.setPlanCode("PL1");
            req.setCoverageCode("C1");
            req.setEffectiveDate(Instant.now().plusSeconds(3600));

            when(policyRepository.save(any())).thenAnswer(invocation -> {
                com.claimassist.platform.policy_service.entity.Policy p = invocation.getArgument(0);
                if (p.getId() == null) p.setId(10L);
                return p;
            });
            com.claimassist.platform.policy_service.entity.Policy found = com.claimassist.platform.policy_service.entity.Policy.builder().id(10L).policyNumber("POL-42-000001").stripePaymentIntentId("pi_test_123").status("PENDING_PAYMENT").build();
            when(policyRepository.findById(10L)).thenReturn(java.util.Optional.of(found));

            service.createPolicy(req, "42", "idem-2");

            assertThat(persistedVersion.get()).isNotNull();
            assertThat(persistedVersion.get().getPremiumCents()).isNull();
            assertThat(persistedVersion.get().getDeductibleCents()).isEqualTo(1000L);
        }
    }
}
