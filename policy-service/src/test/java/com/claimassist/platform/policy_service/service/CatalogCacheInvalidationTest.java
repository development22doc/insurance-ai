package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.policy_service.config.RedisCacheConfig;
import com.claimassist.platform.policy_service.dto.PlanCreateRequest;
import com.claimassist.platform.policy_service.dto.ProductCreateRequest;
import com.claimassist.platform.policy_service.dto.ProductStatusUpdateRequest;
import com.claimassist.platform.policy_service.dto.ProductUpdateRequest;
import com.claimassist.platform.policy_service.dto.PlanStatusUpdateRequest;
import com.claimassist.platform.policy_service.dto.PlanUpdateRequest;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogCacheInvalidationTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final PlanRepository planRepository = mock(PlanRepository.class);
    private final CacheManager cacheManager = mock(CacheManager.class);
    private final Cache productCache = mock(Cache.class);
    private final Cache planCache = mock(Cache.class);
    private final Cache plansByProductCache = mock(Cache.class);

    private ProductCatalogCommandService productCatalogCommandService;
    private PlanCatalogCommandService planCatalogCommandService;

    @BeforeEach
    void setUp() {
        when(cacheManager.getCache(RedisCacheConfig.PRODUCT_CACHE)).thenReturn(productCache);
        when(cacheManager.getCache(RedisCacheConfig.PLAN_CACHE)).thenReturn(planCache);
        when(cacheManager.getCache(RedisCacheConfig.PLANS_BY_PRODUCT_CACHE)).thenReturn(plansByProductCache);

        productCatalogCommandService = new ProductCatalogCommandService(productRepository, cacheManager);
        planCatalogCommandService = new PlanCatalogCommandService(productRepository, planRepository, cacheManager);
    }

    @Test
    void createProduct_invalidatesProductEntryAndAllList() {
        Product saved = Product.builder().id(10L).code("AUTO").name("Auto").active(true).createdAt(Instant.now()).build();
        when(productRepository.findByCodeIgnoreCase("AUTO")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        productCatalogCommandService.createProduct(new ProductCreateRequest("AUTO", "Auto"));

        verify(productCache).evict(10L);
        verify(productCache).evict("all");
    }

    @Test
    void updateProduct_invalidatesProductEntryAndAllList() {
        Product existing = Product.builder().id(11L).code("AUTO").name("Auto").active(true).createdAt(Instant.now()).build();
        when(productRepository.findById(11L)).thenReturn(Optional.of(existing));
        when(productRepository.findByCodeIgnoreCase("AUTO")).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenReturn(existing);

        productCatalogCommandService.updateProduct(11L, new ProductUpdateRequest("AUTO", "Updated Auto"));

        verify(productCache).evict(11L);
        verify(productCache).evict("all");
    }

    @Test
    void updateProductStatus_invalidatesProductEntryAndAllList() {
        Product existing = Product.builder().id(12L).code("AUTO").name("Auto").active(true).createdAt(Instant.now()).build();
        when(productRepository.findById(12L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenReturn(existing);

        productCatalogCommandService.updateProductStatus(12L, new ProductStatusUpdateRequest(false));

        verify(productCache).evict(12L);
        verify(productCache).evict("all");
    }

    @Test
    void createPlan_invalidatesPlansByProductCache() {
        Product product = Product.builder().id(21L).code("AUTO").name("Auto").active(true).createdAt(Instant.now()).build();
        when(productRepository.findById(21L)).thenReturn(Optional.of(product));
        when(planRepository.findByCodeIgnoreCaseAndProductId("PLAT", 21L)).thenReturn(Optional.empty());

        Plan saved = Plan.builder().id(31L).product(product).code("PLAT").name("Platinum").active(true)
                .deductibleCents(1000L).coverageLimitCents(50000L).build();
        when(planRepository.save(any(Plan.class))).thenReturn(saved);

        planCatalogCommandService.createPlan(21L, new PlanCreateRequest("PLAT", "Platinum", 1000L, 50000L));

        verify(plansByProductCache).evict(21L);
    }

    @Test
    void updatePlan_invalidatesPlanAndPlansByProductCache() {
        Product product = Product.builder().id(22L).code("AUTO").name("Auto").active(true).createdAt(Instant.now()).build();
        Plan existing = Plan.builder().id(32L).product(product).code("BASIC").name("Basic").active(true)
                .deductibleCents(500L).coverageLimitCents(20000L).build();
        when(planRepository.findById(32L)).thenReturn(Optional.of(existing));
        when(planRepository.findByCodeIgnoreCaseAndProductId("BASIC", 22L)).thenReturn(Optional.of(existing));
        when(planRepository.save(any(Plan.class))).thenReturn(existing);

        planCatalogCommandService.updatePlan(32L, new PlanUpdateRequest("BASIC", "Basic", 600L, 25000L));

        verify(planCache).evict(32L);
        verify(plansByProductCache).evict(22L);
    }

    @Test
    void updatePlanStatus_invalidatesPlanAndPlansByProductCache() {
        Product product = Product.builder().id(23L).code("AUTO").name("Auto").active(true).createdAt(Instant.now()).build();
        Plan existing = Plan.builder().id(33L).product(product).code("BASIC").name("Basic").active(true)
                .deductibleCents(500L).coverageLimitCents(20000L).build();
        when(planRepository.findById(33L)).thenReturn(Optional.of(existing));
        when(planRepository.save(any(Plan.class))).thenReturn(existing);

        planCatalogCommandService.updatePlanStatus(33L, new PlanStatusUpdateRequest(false));

        verify(planCache).evict(33L);
        verify(plansByProductCache).evict(23L);
    }
}
