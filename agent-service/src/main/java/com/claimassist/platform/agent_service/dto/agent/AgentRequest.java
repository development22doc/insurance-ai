package com.claimassist.platform.agent_service.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgentRequest(
        @NotBlank(message = "message must not be empty") String message,
        @NotNull(message = "claimId is required") Long claimId
) {}
