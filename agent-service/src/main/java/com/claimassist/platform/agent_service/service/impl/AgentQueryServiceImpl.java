package com.claimassist.platform.agent_service.service.impl;

import com.claimassist.platform.agent_service.dto.agent.AgentMessageResponse;
import com.claimassist.platform.agent_service.entity.AgentSession;
import com.claimassist.platform.agent_service.entity.AgentSessionId;
import com.claimassist.platform.agent_service.mapper.AgentMapper;
import com.claimassist.platform.agent_service.repository.AgentMessageRepository;
import com.claimassist.platform.agent_service.repository.AgentSessionRepository;
import com.claimassist.platform.agent_service.service.AgentQueryService;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentQueryServiceImpl implements AgentQueryService {

    private final AgentSessionRepository agentSessionRepository;
    private final AgentMessageRepository agentMessageRepository;
    private final AgentMapper agentMapper;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @PreAuthorize("@security.canAccessClaim(#claimId)")
    public List<AgentMessageResponse> getConversationHistory(Long claimId) {
        Long userId = currentUserProvider.getCurrentUserId();
        AgentSession session = agentSessionRepository.getReferenceById(new AgentSessionId(claimId, userId));
        return agentMapper.fromListOfAgentMessage(agentMessageRepository.findByAgentSessionWithEvents(session));
    }
}
