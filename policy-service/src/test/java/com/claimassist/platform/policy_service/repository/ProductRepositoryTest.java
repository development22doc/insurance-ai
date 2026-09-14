package com.claimassist.platform.policy_service.repository;

import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.entity.ProductType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void savesAndReadsProduct() {
        Product product = Product.builder()
                .code("AUTO")
                .name("Auto Insurance")
                .type(ProductType.AUTO)
                .status("ACTIVE")
                .description("Vehicle insurance")
                .build();

        Product saved = productRepository.saveAndFlush(product);

        assertThat(saved.getId()).isNotNull();
        assertThat(productRepository.findByCode("AUTO")).isPresent();
    }
}
