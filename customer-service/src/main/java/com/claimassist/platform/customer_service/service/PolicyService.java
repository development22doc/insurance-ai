package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.dto.policy.PolicyCreateRequest;
import com.claimassist.platform.customer_service.dto.policy.PolicyResponse;
import com.claimassist.platform.customer_service.dto.policy.PolicyUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for Policy CRUD operations.
 * Provides business logic for creating, reading, updating, and deleting policies.
 */
public interface PolicyService {

    /**
     * Creates a new policy for the authenticated customer.
     *
     * @param request The policy creation request
     * @param customerId The authenticated customer's ID
     * @return The created policy response
     */
    PolicyResponse createPolicy(PolicyCreateRequest request, Long customerId, String idempotencyKey);

    /**
     * Retrieves a policy by ID for the authenticated customer.
     *
     * @param policyId The policy ID
     * @param customerId The authenticated customer's ID
     * @return The policy response
     */
    PolicyResponse getPolicyById(Long policyId, Long customerId);

    /**
     * Retrieves all policies for the authenticated customer with pagination.
     *
     * @param customerId The authenticated customer's ID
     * @param pageable Pagination parameters
     * @return Page of policy responses
     */
    Page<PolicyResponse> getPolicies(Long customerId, Pageable pageable);

    /**
     * Updates an existing policy for the authenticated customer.
     *
     * @param policyId The policy ID
     * @param request The policy update request
     * @param customerId The authenticated customer's ID
     * @return The updated policy response
     */
    PolicyResponse updatePolicy(Long policyId, PolicyUpdateRequest request, Long customerId);

    /**
     * Deletes a policy for the authenticated customer.
     *
     * @param policyId The policy ID
     * @param customerId The authenticated customer's ID
     */
    void deletePolicy(Long policyId, Long customerId);

    /**
     * Delegate cancellation to Policy Service lifecycle command.
     * This does not mutate the Customer DB; it forwards the intent to Policy Service.
     */
    java.util.Map<String, Object> cancelPolicy(Long policyId, com.claimassist.platform.customer_service.dto.policy.CancelRequestDto body, Long customerId, String authorizationHeader);

    /**
     * Delegate reinstatement to Policy Service lifecycle command.
     * This does not mutate the Customer DB; it forwards the intent to Policy Service.
     */
    java.util.Map<String, Object> reinstatePolicy(Long policyId, com.claimassist.platform.customer_service.dto.policy.ReinstateRequestDto body, Long customerId, String idempotencyKey, String authorizationHeader);
}

