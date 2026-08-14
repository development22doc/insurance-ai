CREATE TABLE IF NOT EXISTS claim_saga_orchestrations (
    id                     BIGSERIAL PRIMARY KEY,
    saga_id                VARCHAR(255) NOT NULL UNIQUE,
    action                 VARCHAR(64) NOT NULL,
    status                 VARCHAR(64) NOT NULL,
    current_step           VARCHAR(64),
    claim_id               BIGINT,
    policy_id              BIGINT,
    incident_type          VARCHAR(255),
    incident_date          VARCHAR(255),
    estimated_amount_cents BIGINT,
    actor_user_id          BIGINT,
    note                   TEXT,
    idempotency_key        VARCHAR(255),
    attempts               INTEGER NOT NULL DEFAULT 0,
    compensation_required  BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at             TIMESTAMP NOT NULL,
    created_at             TIMESTAMP NOT NULL,
    updated_at             TIMESTAMP NOT NULL,
    last_error             TEXT
);

CREATE INDEX IF NOT EXISTS idx_claim_saga_status_expires
    ON claim_saga_orchestrations (status, expires_at);

CREATE INDEX IF NOT EXISTS idx_claim_saga_updated
    ON claim_saga_orchestrations (updated_at);

CREATE TABLE IF NOT EXISTS saga_processed_messages (
    message_id   VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL
);
