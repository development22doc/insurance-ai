package com.claimassist.platform.agent_service.service;

import com.claimassist.platform.agent_service.dto.agent.AgentMessageResponse;

import java.util.List;

public interface AgentQueryService {
    List<AgentMessageResponse> getConversationHistory(Long claimId);
}
