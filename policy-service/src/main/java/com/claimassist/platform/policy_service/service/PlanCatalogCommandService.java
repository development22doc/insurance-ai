package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.config.RedisCacheConfig;
import com.claimassist.platform.policy_service.dto.PlanCreateRequest;
import com.claimassist.platform.policy_service.dto.PlanDto;
import com.claimassist.platform.policy_service.dto.PlanStatusUpdateRequest;
import com.claimassist.platform.policy_service.dto.PlanUpdateRequest;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class PlanCatalogCommandService {

    private final ProductRepository productRepository;
    private final PlanRepository planRepository;
    private final CacheManager cacheManager;

    @Transactional
    public PlanDto createPlan(Long productId, PlanCreateRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", String.valueOf(productId)));

        String code = normalizeCode(request.code());
        String name = normalizeName(request.name());
        Long deductible = normalizeMonetaryValue(request.deductibleCents(), "Deductible");
        Long coverageLimit = normalizeMonetaryValue(request.coverageLimitCents(), "Coverage limit");

        if (planRepository.findByCodeIgnoreCaseAndProductId(code, productId).isPresent()) {
            throw new BadRequestException("Plan code already exists for product: " + code);
        }

        Plan plan = Plan.builder()
                .product(product)
                .code(code)
                .name(name)
                .active(true)
                .deductibleCents(deductible)
                .coverageLimitCents(coverageLimit)
                .build();

        Plan saved = planRepository.save(plan);
        evictPlansByProductAfterCommit(productId);
        return map(saved);
    }

    @Transactional
    public PlanDto updatePlan(Long planId, PlanUpdateRequest request) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", String.valueOf(planId)));

        String code = normalizeCode(request.code());
        String name = normalizeName(request.name());
        Long deductible = normalizeMonetaryValue(request.deductibleCents(), "Deductible");
        Long coverageLimit = normalizeMonetaryValue(request.coverageLimitCents(), "Coverage limit");

        if (!code.equalsIgnoreCase(plan.getCode())) {
            planRepository.findByCodeIgnoreCaseAndProductId(code, plan.getProduct().getId())
                    .filter(existing -> !existing.getId().equals(planId))
                    .ifPresent(existing -> {
                        throw new BadRequestException("Plan code already exists for product: " + code);
                    });
        }

        plan.setCode(code);
        plan.setName(name);
        plan.setDeductibleCents(deductible);
        plan.setCoverageLimitCents(coverageLimit);
        Plan saved = planRepository.save(plan);
        evictPlanCacheAfterCommit(saved.getId(), saved.getProduct() != null ? saved.getProduct().getId() : null);
        return map(saved);
    }

    @Transactional
    public PlanDto updatePlanStatus(Long planId, PlanStatusUpdateRequest request) {
        if (request == null || request.active() == null) {
            throw new BadRequestException("Plan active flag is required");
        }

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", String.valueOf(planId)));

        plan.setActive(request.active());
        Plan saved = planRepository.save(plan);
        evictPlanCacheAfterCommit(saved.getId(), saved.getProduct() != null ? saved.getProduct().getId() : null);
        return map(saved);
    }

    private void evictPlanCacheAfterCommit(Long planId, Long... productIds) {
        Runnable evictTask = () -> {
            Cache planCache = cacheManager.getCache(RedisCacheConfig.PLAN_CACHE);
            if (planCache != null && planId != null) {
                try {
                    planCache.evict(planId);
                } catch (Exception e) {
                    System.err.println("Failed to evict plan cache key " + planId + ": " + e.getMessage());
                }
            }

            Cache productPlanCache = cacheManager.getCache(RedisCacheConfig.PLANS_BY_PRODUCT_CACHE);
            if (productPlanCache != null) {
                for (Long productId : productIds) {
                    if (productId == null) {
                        continue;
                    }
                    try {
                        productPlanCache.evict(productId);
                    } catch (Exception e) {
                        System.err.println("Failed to evict plansByProduct cache key " + productId + ": " + e.getMessage());
                    }
                }
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictTask.run();
                }
            });
        } else {
            evictTask.run();
        }
    }

    private void evictPlansByProductAfterCommit(Long productId) {
        Runnable evictTask = () -> {
            Cache productPlanCache = cacheManager.getCache(RedisCacheConfig.PLANS_BY_PRODUCT_CACHE);
            if (productPlanCache == null || productId == null) {
                return;
            }
            try {
                productPlanCache.evict(productId);
            } catch (Exception e) {
                System.err.println("Failed to evict plansByProduct cache key " + productId + ": " + e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictTask.run();
                }
            });
        } else {
            evictTask.run();
        }
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Plan code is required");
        }
        String normalized = value.trim();
        if (normalized.length() < 2 || normalized.length() > 64) {
            throw new BadRequestException("Plan code must be between 2 and 64 characters");
        }
        return normalized;
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Plan name is required");
        }
        String normalized = value.trim();
        if (normalized.length() < 2 || normalized.length() > 255) {
            throw new BadRequestException("Plan name must be between 2 and 255 characters");
        }
        return normalized;
    }

    private Long normalizeMonetaryValue(Long value, String fieldName) {
        if (value == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        if (value < 0L) {
            throw new BadRequestException(fieldName + " must be zero or positive");
        }
        return value;
    }

    private PlanDto map(Plan plan) {
        Product product = plan.getProduct();
        return new PlanDto(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getActive(),
                product != null ? product.getId() : null,
                product != null ? product.getCode() : null,
                plan.getDeductibleCents(),
                plan.getCoverageLimitCents(),
                plan.getCreatedAt()
        );
    }
}
