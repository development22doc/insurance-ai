package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.policy_service.dto.PlanDetailDto;
import com.claimassist.platform.policy_service.dto.ProductDetailDto;
import com.claimassist.platform.policy_service.dto.ProductSummaryDto;
import com.claimassist.platform.policy_service.service.ProductCatalogService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class CatalogController {

    private final ProductCatalogService productCatalogService;

    @GetMapping("/products")
    public ResponseEntity<List<ProductSummaryDto>> getProducts() {
        return ResponseEntity.ok(productCatalogService.getProducts());
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductDetailDto> getProduct(@PathVariable @Positive Long productId) {
        return ResponseEntity.ok(productCatalogService.getProduct(productId));
    }

    @GetMapping("/plans/{planId}")
    public ResponseEntity<PlanDetailDto> getPlan(@PathVariable @Positive Long planId) {
        return ResponseEntity.ok(productCatalogService.getPlan(planId));
    }
}
