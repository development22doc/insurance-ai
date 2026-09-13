package com.claimassist.platform.customer_service.controller;

import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.customer_service.dto.customer.CustomerResponse;
import com.claimassist.platform.customer_service.dto.customer.UpdateCustomerRequest;
import com.claimassist.platform.customer_service.service.CustomerLookupService;
import com.claimassist.platform.customer_service.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for customer profile operations.
 * Provides endpoints for retrieving, updating, and deleting customer profiles.
 * Follows the existing project architecture with thin controllers,
 * service layer business logic, and proper authorization.
 */
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final CurrentUserProvider currentUserProvider;
    private final CustomerLookupService customerLookupService;

    /**
     * Retrieves the authenticated customer's profile.
     * Returns the current user's profile based on the authenticated user ID.
     *
     * @return The current customer's profile
     */
    @GetMapping("/me")
    public ResponseEntity<CustomerResponse> getCurrentCustomer() {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        CustomerResponse response = customerLookupService.findById(currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates the authenticated customer's profile.
     * Customers can only update their own profile.
     *
     * @param customerId The ID of the customer to update
     * @param request The update request containing the new profile data
     * @return The updated customer profile
     */
    @PatchMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable Long customerId,
            @RequestBody @Valid UpdateCustomerRequest request) {

        Long requestingUserId = currentUserProvider.getCurrentUserId();
        CustomerResponse response = customerService.updateCustomer(customerId, request, requestingUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes the authenticated customer's account.
     * Customers can only delete their own account.
     *
     * @param customerId The ID of the customer to delete
     * @return 204 No Content on successful deletion
     */
    @DeleteMapping("/{customerId}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long customerId) {
        Long requestingUserId = currentUserProvider.getCurrentUserId();
        customerService.deleteCustomer(customerId, requestingUserId);
        return ResponseEntity.noContent().build();
    }
}
