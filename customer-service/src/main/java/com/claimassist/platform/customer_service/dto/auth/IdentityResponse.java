package com.claimassist.platform.customer_service.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal response after successful authentication.
 * Tokens are transmitted via HttpOnly cookies, not in this response.
 * This response contains only identity information for the frontend to display.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdentityResponse(
        Long customerId,
        String fullName
) {
}

