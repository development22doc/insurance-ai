package com.claimassist.platform.customer_service.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude (JsonInclude.Include.NON_NULL)
public record AuthResponse(

        String accessToken,

        String refreshToken,

        String tokenType,

        Long expiresIn,

        Long refreshExpiresIn,

        String scope,

        String idToken,

        Long customerId,

        String fullName

) {
}