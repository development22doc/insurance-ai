-- V6: Add query indexes for agent-service access patterns.
-- idx_agent_events_message_id supports the JOIN FETCH between agent_messages
-- and agent_events in AgentMessageRepository.findByAgentSessionWithEvents.
CREATE INDEX IF NOT EXISTS idx_agent_events_message_id
    ON agent_events (agent_message_id);

-- idx_agent_messages_session_created supports
-- AgentMessageRepository.findByAgentSessionWithEvents:
-- WHERE claim_id = ? AND user_id = ? ORDER BY created_at ASC
-- (equality columns first, sort column last).
CREATE INDEX IF NOT EXISTS idx_agent_messages_session_created
    ON agent_messages (claim_id, user_id, created_at);