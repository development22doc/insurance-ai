package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.entity.ProductType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PlanRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PlanRepository planRepository;

    @Test
    void savesAndFindsPlansByProductAndStatus() {
        Product product = Product.builder()
                .code("HEALTH")
                .name("Health Insurance")
                .type(ProductType.HEALTH)
                .status("ACTIVE")
                .description("Healthcare cover")
                .build();

        Product savedProduct = productRepository.saveAndFlush(product);

        Plan plan = Plan.builder()
                .product(savedProduct)
                .code("HEALTH_STANDARD")
                .name("Standard Health")
                .status("ACTIVE")
                .annualPremiumCents(120000L)
                .deductibleCents(30000L)
                .coverageLimitCents(3500000L)
                .currency("INR")
                .build();

        Plan savedPlan = planRepository.saveAndFlush(plan);

        assertThat(savedPlan.getId()).isNotNull();
        assertThat(planRepository.findByProductIdAndStatus(savedProduct.getId(), "ACTIVE")).hasSize(1);
        assertThat(planRepository.findByIdAndStatus(savedPlan.getId(), "ACTIVE")).isPresent();
    }
}
