package com.claimassist.platform.customer_service.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Email @NotBlank String username,
        @NotBlank @Size(min = 1, max = 60) String fullName,
        @Size(min = 8, message = "password must be at least 8 characters") String password
) {}
