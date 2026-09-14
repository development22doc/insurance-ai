package com.claimassist.platform.policy_service.dto;

public record ProductSummaryDto(
        Long id,
        String code,
        String name,
        String type,
        String description,
        String status
) {
}
