package com.claimassist.platform.policy_service.dto;

import java.util.List;

public record ProductDetailDto(
        Long id,
        String code,
        String name,
        String type,
        String description,
        String status,
        List<PlanSummaryDto> plans
) {
}
