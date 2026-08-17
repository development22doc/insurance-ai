package com.claimassist.platform.agent_service.memory;

import com.claimassist.platform.agent_service.config.AgentAiProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The memory retrieval side of conversation management. Given a conversation
 * (an {@code AgentSession}), it loads the most recent, bounded conversation
 * history in chronological order, ready to be turned into LLM context.
 * <p>
 * Persistence of turns stays in {@code AgentTurnPersistenceService} (the write
 * side, unchanged). This service is deliberately read-only and bounded: it
 * never loads the entire unbounded history, which is what prevents the context
 * window from growing without limit.
 */
@Service
public class ConversationMemoryService {

    private final com.claimassist.platform.agent_service.repository.AgentMessageRepository agentMessageRepository;
    private final AgentAiProperties properties;

    public ConversationMemoryService(
            com.claimassist.platform.agent_service.repository.AgentMessageRepository agentMessageRepository,
            AgentAiProperties properties) {
        this.agentMessageRepository = agentMessageRepository;
        this.properties = properties;
    }

    /**
     * Load the most recent {@code maxContextMessages} USER/ASSISTANT messages
     * for the given conversation, oldest first. Returns an empty list when the
     * limit is zero/negative or there is no history yet.
     */
    public List<ConversationMessage> loadRecent(
            com.claimassist.platform.agent_service.entity.AgentSession session) {
        int limit = properties.getMaxContextMessages();
        if (session == null || limit <= 0) {
            return List.of();
        }
        List<com.claimassist.platform.agent_service.entity.AgentMessage> recent =
                agentMessageRepository.findRecentByAgentSession(
                        session, org.springframework.data.domain.PageRequest.of(0, limit));

        // The query returns newest-first; reverse to chronological and keep only
        // the message roles that belong in LLM context.
        List<ConversationMessage> result = new ArrayList<>(recent.size());
        for (int i = recent.size() - 1; i >= 0; i--) {
            com.claimassist.platform.agent_service.entity.AgentMessage m = recent.get(i);
            if (m.getRole() == com.claimassist.platform.common_lib.enums.MessageRole.USER
                    || m.getRole() == com.claimassist.platform.common_lib.enums.MessageRole.ASSISTANT) {
                result.add(new ConversationMessage(m.getRole(), m.getContent()));
            }
        }
        return Collections.unmodifiableList(result);
    }
}