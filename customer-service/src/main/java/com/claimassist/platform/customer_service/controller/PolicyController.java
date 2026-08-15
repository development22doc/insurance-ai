package com.claimassist.platform.customer_service.controller;

import com.claimassist.platform.customer_service.dto.policy.PolicyCreateRequest;
import com.claimassist.platform.customer_service.dto.policy.PolicyResponse;
import com.claimassist.platform.customer_service.dto.policy.PolicyUpdateRequest;
import com.claimassist.platform.customer_service.service.PolicyQueryService;
import com.claimassist.platform.customer_service.service.PolicyService;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Policy CRUD operations.
 * Provides endpoints for creating, reading, updating, and deleting policies.
 * Follows the existing project architecture with thin controllers,
 * service layer business logic, and proper authorization.
 * Customers can only access their own policies.
 */
@RestController
@RequestMapping("/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;
    private final PolicyQueryService policyQueryService;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Creates a new policy for the authenticated customer.
     *
     * @param request The policy creation request
     * @return The created policy response
     */
    @PostMapping
    public ResponseEntity<PolicyResponse> createPolicy(@RequestBody @Valid PolicyCreateRequest request) {
        Long customerId = currentUserProvider.getCurrentUserId();
        PolicyResponse response = policyService.createPolicy(request, customerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all policies for the authenticated customer.
     * This endpoint uses the cached query service for optimal performance.
     *
     * @return List of policy responses
     */
    @GetMapping("/all")
    public ResponseEntity<List<PolicyResponse>> getMyPolicies() {
        Long customerId = currentUserProvider.getCurrentUserId();
        List<PolicyResponse> policies = policyQueryService.getMyPolicies(customerId);
        return ResponseEntity.ok(policies);
    }

    /**
     * Retrieves a specific policy by ID for the authenticated customer.
     *
     * @param policyId The policy ID
     * @return The policy response
     */
    @GetMapping("/{policyId}")
    public ResponseEntity<PolicyResponse> getPolicyById(@PathVariable Long policyId) {
        Long customerId = currentUserProvider.getCurrentUserId();
        PolicyResponse response = policyService.getPolicyById(policyId, customerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing policy for the authenticated customer.
     *
     * @param policyId The policy ID
     * @param request The policy update request
     * @return The updated policy response
     */
    @PutMapping("/{policyId}")
    public ResponseEntity<PolicyResponse> updatePolicy(
            @PathVariable Long policyId,
            @RequestBody @Valid PolicyUpdateRequest request) {
        Long customerId = currentUserProvider.getCurrentUserId();
        PolicyResponse response = policyService.updatePolicy(policyId, request, customerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a policy for the authenticated customer.
     *
     * @param policyId The policy ID
     * @return 204 No Content on successful deletion
     */
    @DeleteMapping("/{policyId}")
    public ResponseEntity<Void> deletePolicy(@PathVariable Long policyId) {
        Long customerId = currentUserProvider.getCurrentUserId();
        policyService.deletePolicy(policyId, customerId);
        return ResponseEntity.noContent().build();
    }
}
