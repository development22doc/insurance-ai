package com.claimassist.platform.agent_service.memory;

import com.claimassist.platform.agent_service.config.AgentAiProperties;
import com.claimassist.platform.agent_service.entity.AgentMessage;
import com.claimassist.platform.agent_service.entity.AgentSession;
import com.claimassist.platform.agent_service.entity.AgentSessionId;
import com.claimassist.platform.agent_service.repository.AgentMessageRepository;
import com.claimassist.platform.common_lib.enums.MessageRole;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationMemoryServiceTest {

    private final AgentSession session =
            AgentSession.builder().id(new AgentSessionId(99L, 42L)).build();

    private AgentMessage msg(long id, MessageRole role, String content) {
        return AgentMessage.builder().id(id).role(role).content(content).build();
    }

    @Test
    void returnsChronologicalUserAssistantMessages() {
        AgentMessageRepository repo = mock(AgentMessageRepository.class);
        // query returns newest-first
        when(repo.findRecentByAgentSession(any(), any(Pageable.class))).thenReturn(List.of(
                msg(30L, MessageRole.ASSISTANT, "answer2"),
                msg(20L, MessageRole.USER, "question2"),
                msg(10L, MessageRole.USER, "question1"),
                msg(5L, MessageRole.ASSISTANT, "answer1")));

        ConversationMemoryService svc = new ConversationMemoryService(repo, new AgentAiProperties());
        List<ConversationMessage> history = svc.loadRecent(session);

        assertThat(history).extracting(ConversationMessage::content)
                .containsExactly("answer1", "question1", "question2", "answer2");
    }

    @Test
    void excludesSystemAndToolRolesFromContext() {
        AgentMessageRepository repo = mock(AgentMessageRepository.class);
        when(repo.findRecentByAgentSession(any(), any(Pageable.class))).thenReturn(List.of(
                msg(4L, MessageRole.SYSTEM, "hidden"),
                msg(3L, MessageRole.ASSISTANT, "ok"),
                msg(2L, MessageRole.TOOL, "tool payload")));

        ConversationMemoryService svc = new ConversationMemoryService(repo, new AgentAiProperties());
        List<ConversationMessage> history = svc.loadRecent(session);

        assertThat(history).extracting(ConversationMessage::role)
                .containsExactly(MessageRole.ASSISTANT);
    }

    @Test
    void respectsMessageCountLimit() {
        AgentMessageRepository repo = mock(AgentMessageRepository.class);
        // The repository applies PageRequest.of(0, limit) so it returns only the
        // newest `limit` messages, newest-first, as a real DB would.
        when(repo.findRecentByAgentSession(any(), any(Pageable.class))).thenReturn(List.of(
                msg(6L, MessageRole.USER, "6"), msg(5L, MessageRole.USER, "5")));
        AgentAiProperties small = new AgentAiProperties();
        small.setMaxContextMessages(2);
        ConversationMemoryService svc = new ConversationMemoryService(repo, small);

        assertThat(svc.loadRecent(session)).hasSize(2);
        assertThat(svc.loadRecent(session)).extracting(ConversationMessage::content)
                .containsExactly("5", "6");
    }

    @Test
    void returnsEmptyWhenNoHistory() {
        AgentMessageRepository repo = mock(AgentMessageRepository.class);
        when(repo.findRecentByAgentSession(any(), any(Pageable.class))).thenReturn(List.of());
        ConversationMemoryService svc = new ConversationMemoryService(repo, new AgentAiProperties());
        assertThat(svc.loadRecent(session)).isEmpty();
    }

    @Test
    void returnsEmptyWhenLimitIsZeroOrSessionNull() {
        AgentMessageRepository repo = mock(AgentMessageRepository.class);
        AgentAiProperties zero = new AgentAiProperties();
        zero.setMaxContextMessages(0);
        ConversationMemoryService svc = new ConversationMemoryService(repo, zero);
        assertThat(svc.loadRecent(session)).isEmpty();
        assertThat(svc.loadRecent(null)).isEmpty();
    }
}