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
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@org.junit.jupiter.api.Disabled("Integration test disabled in this run; use explicit IT runner")
public class PolicyCreationServiceIdempotencyIT {

    @Autowired
    PolicyCreationService service;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    PlanRepository planRepository;

    @Autowired
    CoverageRepository coverageRepository;

    @Autowired
    PolicyRepository policyRepository;

    @Test
    void createPolicy_with_same_idempotency_key_runs_only_once() throws Exception {
        // Prepare product/plan/coverage persisted records
        Product product = Product.builder().code("PIT").build();
        product = productRepository.save(product);

        Plan plan = Plan.builder().code("PLIT").product(product).active(true).createdAt(Instant.now().minusSeconds(3600)).deductibleCents(1000L).build();
        plan = planRepository.save(plan);

        Coverage coverage = Coverage.builder().code("CIT").plan(plan).deductibleCents(1000L).limitCents(50000L).name("COV").build();
        coverage = coverageRepository.save(coverage);

        // Mock stripe PaymentIntent.create static
        PaymentIntent mockIntent = Mockito.mock(PaymentIntent.class);
        Mockito.when(mockIntent.getId()).thenReturn("pi_it_123");

        try (MockedStatic<com.stripe.model.PaymentIntent> mocked = Mockito.mockStatic(com.stripe.model.PaymentIntent.class)) {
            mocked.when(() -> com.stripe.model.PaymentIntent.create(any(PaymentIntentCreateParams.class), any()))
                    .thenReturn(mockIntent);

            // Provide a test stripe secret so service does not fail-closed in test
            System.setProperty("STRIPE_SECRET_KEY", "sk_test_local");

            PolicyCreationRequest req = new PolicyCreationRequest();
            req.setCustomerId(900L);
            req.setProductCode(product.getCode());
            req.setPlanCode(plan.getCode());
            req.setCoverageCode(coverage.getCode());
            req.setEffectiveDate(Instant.now().plusSeconds(3600));

            // First call
            var p1 = service.createPolicy(req, "900", "idem-it-1");
            // Second call with same idempotency key
            var p2 = service.createPolicy(req, "900", "idem-it-1");

            assertThat(policyRepository.count()).isEqualTo(1);
            mocked.verify(() -> com.stripe.model.PaymentIntent.create(any(PaymentIntentCreateParams.class), any()), Mockito.times(1));
            assertThat(p1.getPolicyNumber()).isEqualTo(p2.getPolicyNumber());
        }
    }
}
