package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.config.RedisCacheConfig;
import com.claimassist.platform.policy_service.dto.PlanDetailDto;
import com.claimassist.platform.policy_service.dto.PlanSummaryDto;
import com.claimassist.platform.policy_service.dto.ProductDetailDto;
import com.claimassist.platform.policy_service.dto.ProductSummaryDto;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductCatalogService {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final ProductRepository productRepository;
    private final PlanRepository planRepository;

    @Cacheable(value = RedisCacheConfig.PRODUCTS_CACHE, key = "'all'", sync = true)
    public List<ProductSummaryDto> getProducts() {
        log.debug("Loading active products from repository");
        return productRepository.findByStatus(ACTIVE_STATUS).stream()
                .map(this::toProductSummary)
                .toList();
    }

    @Cacheable(value = RedisCacheConfig.PRODUCT_DETAILS_CACHE, key = "#productId", sync = true)
    public ProductDetailDto getProduct(Long productId) {
        Product product = productRepository.findByIdAndStatus(productId, ACTIVE_STATUS)
                .orElseThrow(() -> new ResourceNotFoundException("Product", String.valueOf(productId)));

        List<PlanSummaryDto> plans = planRepository.findByProductIdAndStatus(product.getId(), ACTIVE_STATUS)
                .stream()
                .map(this::toPlanSummary)
                .toList();

        return new ProductDetailDto(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getType().name(),
                product.getDescription(),
                product.getStatus(),
                plans
        );
    }

    @Cacheable(value = RedisCacheConfig.PLAN_DETAILS_CACHE, key = "#planId", sync = true)
    public PlanDetailDto getPlan(Long planId) {
        Plan plan = planRepository.findByIdAndStatus(planId, ACTIVE_STATUS)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", String.valueOf(planId)));

        Product product = plan.getProduct();
        return new PlanDetailDto(
                plan.getId(),
                product.getId(),
                product.getCode(),
                product.getName(),
                plan.getCode(),
                plan.getName(),
                plan.getStatus(),
                plan.getAnnualPremiumCents(),
                plan.getDeductibleCents(),
                plan.getCoverageLimitCents(),
                plan.getCurrency()
        );
    }

    @CacheEvict(value = {RedisCacheConfig.PRODUCTS_CACHE, RedisCacheConfig.PRODUCT_DETAILS_CACHE, RedisCacheConfig.PLAN_DETAILS_CACHE}, allEntries = true)
    public void clearCatalogCache() {
        log.info("Clearing catalog cache entries after future product or plan mutation");
    }

    private ProductSummaryDto toProductSummary(Product product) {
        return new ProductSummaryDto(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getType().name(),
                product.getDescription(),
                product.getStatus()
        );
    }

    private PlanSummaryDto toPlanSummary(Plan plan) {
        return new PlanSummaryDto(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getStatus(),
                plan.getAnnualPremiumCents(),
                plan.getDeductibleCents(),
                plan.getCoverageLimitCents(),
                plan.getCurrency()
        );
    }
}
