package com.claimassist.platform.agent_service.service.impl;

import com.claimassist.platform.agent_service.dto.agent.AgentMessageResponse;
import com.claimassist.platform.agent_service.entity.AgentMessage;
import com.claimassist.platform.agent_service.entity.AgentSession;
import com.claimassist.platform.agent_service.entity.AgentSessionId;
import com.claimassist.platform.agent_service.mapper.AgentMapper;
import com.claimassist.platform.agent_service.repository.AgentMessageRepository;
import com.claimassist.platform.agent_service.repository.AgentSessionRepository;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentQueryServiceImplTest {

    @Test
    void getConversationHistoryResolvesSessionByClaimAndUserAndMapsMessages() {
        AgentSessionRepository sessionRepo = mock(AgentSessionRepository.class);
        AgentMessageRepository messageRepo = mock(AgentMessageRepository.class);
        AgentMapper mapper = mock(AgentMapper.class);
        CurrentUserProvider userProvider = mock(CurrentUserProvider.class);

        long claimId = 99L;
        long userId = 7L;
        when(userProvider.getCurrentUserId()).thenReturn(userId);
        AgentSession session = new AgentSession(new AgentSessionId(claimId, userId));
        when(sessionRepo.getReferenceById(any(AgentSessionId.class))).thenReturn(session);
        List<AgentMessage> messages = List.of(new AgentMessage());
        when(messageRepo.findByAgentSessionWithEvents(session)).thenReturn(messages);
        List<AgentMessageResponse> expected = List.of(mock(AgentMessageResponse.class));
        when(mapper.fromListOfAgentMessage(messages)).thenReturn(expected);

        AgentQueryServiceImpl service = new AgentQueryServiceImpl(sessionRepo, messageRepo, mapper, userProvider);

        List<AgentMessageResponse> result = service.getConversationHistory(claimId);

        assertThat(result).isSameAs(expected);
        verify(sessionRepo).getReferenceById(new AgentSessionId(claimId, userId));
        verify(messageRepo).findByAgentSessionWithEvents(session);
    }
}