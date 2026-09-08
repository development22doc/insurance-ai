package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.policy_service.dto.*;
import com.claimassist.platform.policy_service.service.PlanCatalogCommandService;
import com.claimassist.platform.policy_service.service.ProductCatalogCommandService;
import com.claimassist.platform.policy_service.service.PublicPolicyQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PublicPolicyQueryService publicPolicyQueryService;
    private final ProductCatalogCommandService productCatalogCommandService;
    private final PlanCatalogCommandService planCatalogCommandService;
    private final CurrentUserProvider currentUserProvider;
    private final com.claimassist.platform.policy_service.repository.PolicyRepository policyRepository;
    private final com.claimassist.platform.policy_service.service.PolicyLifecycleService policyLifecycleService;
    private final com.claimassist.platform.policy_service.service.PolicyLookupService policyLookupService;

    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS')")
    public ResponseEntity<ProductDto> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.ok(productCatalogCommandService.createProduct(request));
    }

    @PutMapping("/products/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS')")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable Long productId,
                                                  @Valid @RequestBody ProductUpdateRequest request) {
        return ResponseEntity.ok(productCatalogCommandService.updateProduct(productId, request));
    }

    @PatchMapping("/products/{productId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS')")
    public ResponseEntity<ProductDto> updateProductStatus(@PathVariable Long productId,
                                                        @Valid @RequestBody ProductStatusUpdateRequest request) {
        return ResponseEntity.ok(productCatalogCommandService.updateProductStatus(productId, request));
    }

    @PostMapping("/products/{productId}/plans")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS')")
    public ResponseEntity<PlanDto> createPlan(@PathVariable Long productId,
                                             @Valid @RequestBody PlanCreateRequest request) {
        return ResponseEntity.ok(planCatalogCommandService.createPlan(productId, request));
    }

    @PutMapping("/plans/{planId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS')")
    public ResponseEntity<PlanDto> updatePlan(@PathVariable Long planId,
                                             @Valid @RequestBody PlanUpdateRequest request) {
        return ResponseEntity.ok(planCatalogCommandService.updatePlan(planId, request));
    }

    @PatchMapping("/plans/{planId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS')")
    public ResponseEntity<PlanDto> updatePlanStatus(@PathVariable Long planId,
                                                   @Valid @RequestBody PlanStatusUpdateRequest request) {
        return ResponseEntity.ok(planCatalogCommandService.updatePlanStatus(planId, request));
    }

    /**
     * GET /api/v1/policies - Get all policies with pagination (admin/operations use).
     * Requires ADMIN or OPERATIONS role.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS')")
    public ResponseEntity<List<PolicySummaryDto>> getAllPolicies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "-createdAt") String sort) {
        return ResponseEntity.ok(publicPolicyQueryService.getAllPolicies(page, size, sort));
    }

    /**
     * GET /api/v1/policies/my - Get current authenticated customer's policies.
     * Derives customer ID from authenticated JWT, not from client input.
     */
    @GetMapping("/my")
    public ResponseEntity<List<PolicySummaryDto>> getMyPolicies() {
        Long customerId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(publicPolicyQueryService.getMyPolicies(customerId));
    }

    /**
     * GET /api/v1/policies/{policyId}/history - Get policy version history.
     * Customer can only view their own policy history.
     */
    @GetMapping("/{policyId}/history")
    public ResponseEntity<List<PolicyVersionDto>> getPolicyHistory(@PathVariable Long policyId) {
        Long customerId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(publicPolicyQueryService.getPolicyHistory(policyId, customerId));
    }

    /**
     * GET /api/v1/policies/{policyId} - public single-policy retrieval.
     * - Authenticated customers may retrieve only their own policy
     * - ADMIN and OPERATIONS may retrieve any policy
     */
    @GetMapping("/{policyId}")
    public ResponseEntity<PolicySummaryDto> getPolicy(@PathVariable Long policyId) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        com.claimassist.platform.policy_service.entity.Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new com.claimassist.platform.common_lib.error.ResourceNotFoundException("Policy", String.valueOf(policyId)));

        if (isAdminOrOperations()) {
            return ResponseEntity.ok(policyLookupService.getPolicyForAdmin(policyId));
        }

        // Owner-only path: ensure caller owns the policy
        if (policy.getCustomerId() == null || !policy.getCustomerId().equals(currentUserId)) {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed to view this policy");
        }
        return ResponseEntity.ok(policyLookupService.getPolicy(policyId, currentUserId));
    }

    /**
     * GET /api/v1/policies/products - Get all products for public catalog.
     * Public endpoint for product discovery.
     */
    @GetMapping("/products")
    public ResponseEntity<List<ProductDto>> getAllProducts() {
        return ResponseEntity.ok(publicPolicyQueryService.getAllProducts());
    }

    /**
     * GET /api/v1/policies/products/{productId} - Get a specific product.
     * Public endpoint for product details.
     */
    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductDto> getProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(publicPolicyQueryService.getProduct(productId));
    }

    /**
     * GET /api/v1/policies/products/{productId}/plans - Get all plans for a product.
     * Public endpoint for plan discovery.
     */
    @GetMapping("/products/{productId}/plans")
    public ResponseEntity<List<PlanDto>> getPlansForProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(publicPolicyQueryService.getPlansForProduct(productId));
    }

    /**
     * GET /api/v1/policies/plans/{planId} - Get a specific plan.
     * Public endpoint for plan details.
     */
    @GetMapping("/plans/{planId}")
    public ResponseEntity<PlanDto> getPlan(@PathVariable Long planId) {
        return ResponseEntity.ok(publicPolicyQueryService.getPlan(planId));
    }

    // --- Lifecycle command endpoints -------------------------------------------------

    @PostMapping("/{policyId}/issue")
    public ResponseEntity<?> issuePolicy(@PathVariable Long policyId, @RequestBody(required = false) com.claimassist.platform.policy_service.dto.IssueRequestDto body) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        com.claimassist.platform.policy_service.entity.Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new com.claimassist.platform.common_lib.error.ResourceNotFoundException("Policy", String.valueOf(policyId)));

        // Authorization: owner or admin/operations
        if (!isAdminOrOperations() && (policy.getCustomerId() == null || !policy.getCustomerId().equals(currentUserId))) {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed to issue this policy");
        }

        String suppliedPi = body == null ? null : body.stripePaymentIntentId;
        com.claimassist.platform.policy_service.entity.Policy updated = policyLifecycleService.issue(policyId, suppliedPi);
        return ResponseEntity.ok(Map.of("policyId", updated.getId(), "status", updated.getStatus()));
    }

    @PostMapping("/{policyId}/endorse")
    public ResponseEntity<?> endorsePolicy(@PathVariable Long policyId,
                                           @RequestBody com.claimassist.platform.policy_service.dto.EndorseRequestDto body,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        com.claimassist.platform.policy_service.entity.Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new com.claimassist.platform.common_lib.error.ResourceNotFoundException("Policy", String.valueOf(policyId)));
        if (!isAdminOrOperations() && (policy.getCustomerId() == null || !policy.getCustomerId().equals(currentUserId))) {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed to endorse this policy");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new com.claimassist.platform.common_lib.error.BadRequestException("Idempotency-Key header is required for endorsement");
        }

        com.claimassist.platform.policy_service.entity.Policy updated = policyLifecycleService.endorse(policyId, body, idempotencyKey);
        return ResponseEntity.ok(Map.of("policyId", updated.getId(), "status", updated.getStatus()));
    }

    @PostMapping("/{policyId}/renew")
    public ResponseEntity<?> renewPolicy(@PathVariable Long policyId,
                                         @RequestBody com.claimassist.platform.policy_service.dto.RenewRequestDto body,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        com.claimassist.platform.policy_service.entity.Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new com.claimassist.platform.common_lib.error.ResourceNotFoundException("Policy", String.valueOf(policyId)));
        if (!isAdminOrOperations() && (policy.getCustomerId() == null || !policy.getCustomerId().equals(currentUserId))) {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed to renew this policy");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new com.claimassist.platform.common_lib.error.BadRequestException("Idempotency-Key header is required for renewal");
        }

        com.claimassist.platform.policy_service.entity.Policy updated = policyLifecycleService.renew(policyId, body, idempotencyKey);
        return ResponseEntity.ok(Map.of("policyId", updated.getId(), "status", updated.getStatus()));
    }

    @PostMapping("/{policyId}/cancel")
    public ResponseEntity<?> cancelPolicy(@PathVariable Long policyId,
                                          @RequestBody(required = false) com.claimassist.platform.policy_service.dto.CancelRequestDto body) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        com.claimassist.platform.policy_service.entity.Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new com.claimassist.platform.common_lib.error.ResourceNotFoundException("Policy", String.valueOf(policyId)));
        if (!isAdminOrOperations() && (policy.getCustomerId() == null || !policy.getCustomerId().equals(currentUserId))) {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed to cancel this policy");
        }

        com.claimassist.platform.policy_service.entity.Policy updated = policyLifecycleService.cancel(policyId, body == null ? new com.claimassist.platform.policy_service.dto.CancelRequestDto() : body);
        return ResponseEntity.ok(Map.of("policyId", updated.getId(), "status", updated.getStatus()));
    }

    @PostMapping("/{policyId}/reinstate")
    public ResponseEntity<?> reinstatePolicy(@PathVariable Long policyId,
                                             @RequestBody(required = false) com.claimassist.platform.policy_service.dto.ReinstateRequestDto body,
                                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        com.claimassist.platform.policy_service.entity.Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new com.claimassist.platform.common_lib.error.ResourceNotFoundException("Policy", String.valueOf(policyId)));
        if (!isAdminOrOperations() && (policy.getCustomerId() == null || !policy.getCustomerId().equals(currentUserId))) {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed to reinstate this policy");
        }

        com.claimassist.platform.policy_service.entity.Policy updated = policyLifecycleService.reinstate(policyId, body == null ? new com.claimassist.platform.policy_service.dto.ReinstateRequestDto() : body, currentUserId, idempotencyKey);
        return ResponseEntity.ok(Map.of("policyId", updated.getId(), "status", updated.getStatus()));
    }

    private boolean isAdminOrOperations() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_OPERATIONS".equals(a.getAuthority()));
    }
}
