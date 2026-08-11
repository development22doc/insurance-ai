package com.claimassist.platform.customer_service.dto.customer;

/**
 * Response DTO for customer operations.
 * Exposes only the fields that are safe to return to clients.
 * Internal fields like keycloakId are not exposed.
 */
public record CustomerResponse(
        Long id,
        String username,
        String fullName,
        String kycStatus
) {}
