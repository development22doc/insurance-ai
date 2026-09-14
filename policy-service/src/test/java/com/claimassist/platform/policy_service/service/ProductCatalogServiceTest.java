package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.dto.PlanDetailDto;
import com.claimassist.platform.policy_service.dto.ProductDetailDto;
import com.claimassist.platform.policy_service.dto.ProductSummaryDto;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.entity.ProductType;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PlanRepository planRepository;

    @InjectMocks
    private ProductCatalogService productCatalogService;

    @Test
    void getProducts_returnsOnlyActiveCatalogEntries() {
        Product product = Product.builder()
                .id(1L)
                .code("AUTO")
                .name("Auto Insurance")
                .type(ProductType.AUTO)
                .status("ACTIVE")
                .description("Vehicle cover")
                .build();

        when(productRepository.findByStatus("ACTIVE")).thenReturn(List.of(product));

        List<ProductSummaryDto> products = productCatalogService.getProducts();

        assertThat(products).hasSize(1);
        assertThat(products.getFirst().code()).isEqualTo("AUTO");
    }

    @Test
    void getProduct_returnsActivePlans() {
        Product product = Product.builder()
                .id(10L)
                .code("HOME")
                .name("Home Insurance")
                .type(ProductType.HOME)
                .status("ACTIVE")
                .description("Property cover")
                .build();

        Plan plan = Plan.builder()
                .id(99L)
                .product(product)
                .code("HOME_STANDARD")
                .name("Standard Home")
                .status("ACTIVE")
                .annualPremiumCents(150000L)
                .deductibleCents(25000L)
                .coverageLimitCents(5000000L)
                .currency("INR")
                .build();

        when(productRepository.findByIdAndStatus(10L, "ACTIVE")).thenReturn(Optional.of(product));
        when(planRepository.findByProductIdAndStatus(10L, "ACTIVE")).thenReturn(List.of(plan));

        ProductDetailDto productDetail = productCatalogService.getProduct(10L);

        assertThat(productDetail.name()).isEqualTo("Home Insurance");
        assertThat(productDetail.plans()).hasSize(1);
        assertThat(productDetail.plans().getFirst().code()).isEqualTo("HOME_STANDARD");
    }

    @Test
    void getProduct_throwsWhenProductIsMissingOrInactive() {
        when(productRepository.findByIdAndStatus(999L, "ACTIVE")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productCatalogService.getProduct(999L));
    }

    @Test
    void getPlan_returnsPlanDetails() {
        Product product = Product.builder()
                .id(22L)
                .code("TRAVEL")
                .name("Travel Insurance")
                .type(ProductType.TRAVEL)
                .status("ACTIVE")
                .description("Travel cover")
                .build();

        Plan plan = Plan.builder()
                .id(77L)
                .product(product)
                .code("TRAVEL_PLUS")
                .name("Travel Plus")
                .status("ACTIVE")
                .annualPremiumCents(9000L)
                .deductibleCents(2000L)
                .coverageLimitCents(250000L)
                .currency("INR")
                .build();

        when(planRepository.findByIdAndStatus(77L, "ACTIVE")).thenReturn(Optional.of(plan));

        PlanDetailDto planDetail = productCatalogService.getPlan(77L);

        assertThat(planDetail.productName()).isEqualTo("Travel Insurance");
        assertThat(planDetail.coverageLimitCents()).isEqualTo(250000L);
    }

    @Test
    void getPlan_throwsWhenPlanIsMissingOrInactive() {
        when(planRepository.findByIdAndStatus(404L, "ACTIVE")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productCatalogService.getPlan(404L));
    }
}
