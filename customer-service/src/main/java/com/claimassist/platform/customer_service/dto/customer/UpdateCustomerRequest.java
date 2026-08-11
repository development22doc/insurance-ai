package com.claimassist.platform.customer_service.dto.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating customer profile.
 * Only contains fields that are legitimately updateable by the customer.
 * System-managed fields (id, username, keycloakId, stripeCustomerId, kycStatus)
 * are excluded to prevent unauthorized modifications.
 */
public record UpdateCustomerRequest(
        @NotBlank(message = "fullName is required")
        @Size(min = 1, max = 60, message = "fullName must be between 1 and 60 characters")
        String fullName
) {}
