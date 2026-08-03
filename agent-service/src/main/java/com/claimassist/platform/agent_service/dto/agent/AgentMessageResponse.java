package com.claimassist.platform.agent_service.dto.agent;

import com.claimassist.platform.common_lib.enums.MessageRole;

import java.time.Instant;
import java.util.List;

public record AgentMessageResponse(
        Long id,
        MessageRole role,
        String content,
        Integer tokensUsed,
        Instant createdAt,
        List<AgentEventResponse> events
) {}
