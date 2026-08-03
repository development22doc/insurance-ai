package com.claimassist.platform.agent_service.dto.agent;

import com.claimassist.platform.common_lib.enums.AgentEventStatus;
import com.claimassist.platform.common_lib.enums.AgentEventType;

public record AgentEventResponse(
        Long id,
        AgentEventType type,
        AgentEventStatus status,
        Integer sequenceOrder,
        String content,
        String sagaId,
        String proposedStatus
) {}
