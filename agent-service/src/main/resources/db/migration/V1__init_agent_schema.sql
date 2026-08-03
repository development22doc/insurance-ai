CREATE TABLE IF NOT EXISTS agent_sessions (
    claim_id BIGINT NOT NULL,
    user_id  BIGINT NOT NULL,
    PRIMARY KEY (claim_id, user_id)
);

CREATE TABLE IF NOT EXISTS agent_messages (
    id          BIGSERIAL PRIMARY KEY,
    claim_id    BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    role        VARCHAR(32) NOT NULL,
    content     TEXT,
    tokens_used INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL,
    FOREIGN KEY (claim_id, user_id) REFERENCES agent_sessions(claim_id, user_id)
);

-- Permanent audit trail of every AI-agent action, whether accepted or
-- rejected - see AgentEvent's Javadoc. Never Hibernate-ddl-auto-managed.
CREATE TABLE IF NOT EXISTS agent_events (
    id                BIGSERIAL PRIMARY KEY,
    agent_message_id  BIGINT NOT NULL REFERENCES agent_messages(id),
    type              VARCHAR(32) NOT NULL,
    status            VARCHAR(32) NOT NULL,
    sequence_order    INTEGER NOT NULL,
    content           TEXT,
    saga_id           VARCHAR(255),
    proposed_status   VARCHAR(32)
);
CREATE INDEX IF NOT EXISTS idx_agent_events_saga_id ON agent_events (saga_id);

CREATE TABLE IF NOT EXISTS outbox_events (
    id            BIGSERIAL PRIMARY KEY,
    aggregate_id  VARCHAR(255) NOT NULL,
    event_type    VARCHAR(255) NOT NULL,
    topic         VARCHAR(255) NOT NULL,
    partition_key VARCHAR(255),
    payload       TEXT NOT NULL,
    status        VARCHAR(32) NOT NULL,
    created_at    TIMESTAMP NOT NULL,
    published_at  TIMESTAMP,
    attempts      INTEGER NOT NULL DEFAULT 0,
    last_error    TEXT
);
CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_events (status, created_at);
