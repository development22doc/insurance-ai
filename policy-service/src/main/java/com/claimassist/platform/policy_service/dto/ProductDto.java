package com.claimassist.platform.policy_service.dto;

import java.time.Instant;

public record ProductDto(
        Long id,
        String code,
        String name,
        Boolean active,
        Instant createdAt
) {}
