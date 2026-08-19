-- V3: Add event processing and tool execution tracking
ALTER TABLE agent_events
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE agent_events
    ADD COLUMN IF NOT EXISTS tool_call_id VARCHAR(255);

ALTER TABLE agent_events
    ADD COLUMN IF NOT EXISTS tool_name VARCHAR(255);

ALTER TABLE agent_events
    ADD COLUMN IF NOT EXISTS tool_status VARCHAR(32);

ALTER TABLE agent_events
    ADD COLUMN IF NOT EXISTS result_data TEXT;

CREATE INDEX IF NOT EXISTS idx_agent_events_status
    ON agent_events (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_events_tool
    ON agent_events (tool_name, tool_status);

-- Add version for optimistic locking
ALTER TABLE agent_messages
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

