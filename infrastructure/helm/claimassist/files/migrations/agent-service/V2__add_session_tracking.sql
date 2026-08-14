-- V2: Add agent session tracking and message reliability columns
ALTER TABLE agent_sessions
    ADD COLUMN IF NOT EXISTS session_started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE agent_sessions
    ADD COLUMN IF NOT EXISTS session_ended_at TIMESTAMP;

ALTER TABLE agent_sessions
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE agent_messages
    ADD COLUMN IF NOT EXISTS message_id VARCHAR(255);

ALTER TABLE agent_messages
    ADD COLUMN IF NOT EXISTS response_tokens INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_agent_sessions_active
    ON agent_sessions (claim_id, is_active);

CREATE INDEX IF NOT EXISTS idx_agent_messages_created
    ON agent_messages (claim_id, created_at DESC);

