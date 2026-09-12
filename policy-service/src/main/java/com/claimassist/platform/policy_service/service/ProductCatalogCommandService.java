package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.config.RedisCacheConfig;
import com.claimassist.platform.policy_service.dto.ProductCreateRequest;
import com.claimassist.platform.policy_service.dto.ProductDto;
import com.claimassist.platform.policy_service.dto.ProductStatusUpdateRequest;
import com.claimassist.platform.policy_service.dto.ProductUpdateRequest;
import com.claimassist.platform.policy_service.entity.Product;
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
public class ProductCatalogCommandService {

    private final ProductRepository productRepository;
    private final CacheManager cacheManager;

    @Transactional
    public ProductDto createProduct(ProductCreateRequest request) {
        String code = normalizeCode(request.code());
        String name = normalizeName(request.name());

        if (productRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw new BadRequestException("Product code already exists: " + code);
        }

        Product product = Product.builder()
                .code(code)
                .name(name)
                .active(true)
                .build();

        Product saved = productRepository.save(product);
        evictProductCacheAfterCommit(saved.getId(), "all");
        return map(saved);
    }

    @Transactional
    public ProductDto updateProduct(Long productId, ProductUpdateRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", String.valueOf(productId)));

        String code = normalizeCode(request.code());
        String name = normalizeName(request.name());

        if (!code.equalsIgnoreCase(product.getCode())) {
            productRepository.findByCodeIgnoreCase(code)
                    .filter(existing -> !existing.getId().equals(productId))
                    .ifPresent(existing -> {
                        throw new BadRequestException("Product code already exists: " + code);
                    });
        }

        product.setCode(code);
        product.setName(name);
        Product saved = productRepository.save(product);
        evictProductCacheAfterCommit(saved.getId(), "all");
        return map(saved);
    }

    @Transactional
    public ProductDto updateProductStatus(Long productId, ProductStatusUpdateRequest request) {
        if (request == null || request.active() == null) {
            throw new BadRequestException("Product active flag is required");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", String.valueOf(productId)));

        product.setActive(request.active());
        Product saved = productRepository.save(product);
        evictProductCacheAfterCommit(saved.getId(), "all");
        return map(saved);
    }

    private void evictProductCacheAfterCommit(Long productId, Object... keys) {
        Runnable evictTask = () -> {
            Cache cache = cacheManager.getCache(RedisCacheConfig.PRODUCT_CACHE);
            if (cache == null) {
                return;
            }
            for (Object key : keys) {
                if (key == null) {
                    continue;
                }
                try {
                    cache.evict(key);
                } catch (Exception e) {
                    System.err.println("Failed to evict product cache key " + key + " for product " + productId + ": " + e.getMessage());
                }
            }
            if (productId != null) {
                try {
                    cache.evict(productId);
                } catch (Exception e) {
                    System.err.println("Failed to evict product cache key " + productId + " for product " + productId + ": " + e.getMessage());
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

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Product code is required");
        }
        String normalized = value.trim();
        if (normalized.length() < 2 || normalized.length() > 64) {
            throw new BadRequestException("Product code must be between 2 and 64 characters");
        }
        return normalized;
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Product name is required");
        }
        String normalized = value.trim();
        if (normalized.length() < 2 || normalized.length() > 255) {
            throw new BadRequestException("Product name must be between 2 and 255 characters");
        }
        return normalized;
    }

    private ProductDto map(Product product) {
        return new ProductDto(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getActive() == null ? Boolean.TRUE : product.getActive(),
                product.getCreatedAt()
        );
    }
}
