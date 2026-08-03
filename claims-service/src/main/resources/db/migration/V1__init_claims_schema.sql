CREATE TABLE IF NOT EXISTS claims (
    id                    BIGSERIAL PRIMARY KEY,
    claim_number          VARCHAR(64) NOT NULL UNIQUE,
    policy_id             BIGINT NOT NULL,
    incident_type         VARCHAR(64) NOT NULL,
    status                VARCHAR(32) NOT NULL,
    estimated_amount_cents BIGINT,
    approved_amount_cents  BIGINT,
    incident_date         TIMESTAMP NOT NULL,
    created_at            TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP,
    deleted_at            TIMESTAMP
);

CREATE TABLE IF NOT EXISTS claim_parties (
    claim_id    BIGINT NOT NULL REFERENCES claims(id),
    user_id     BIGINT NOT NULL,
    claim_role  VARCHAR(32) NOT NULL,
    added_at    TIMESTAMP NOT NULL,
    PRIMARY KEY (claim_id, user_id)
);

CREATE TABLE IF NOT EXISTS claim_documents (
    id                BIGSERIAL PRIMARY KEY,
    claim_id          BIGINT NOT NULL REFERENCES claims(id),
    path              VARCHAR(512) NOT NULL,
    minio_object_key  VARCHAR(512) NOT NULL,
    doc_type          VARCHAR(64) NOT NULL,
    ocr_status        VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    extracted_text    TEXT,
    fraud_signal_score DOUBLE PRECISION,
    uploaded_at       TIMESTAMP NOT NULL
);

-- Audit table: intentionally never Hibernate-ddl-auto-managed, even during a
-- transition period, since it is the permanent record of every claim status
-- change (including AI-agent-proposed changes) for compliance purposes.
CREATE TABLE IF NOT EXISTS claim_status_history (
    id          BIGSERIAL PRIMARY KEY,
    claim_id    BIGINT NOT NULL REFERENCES claims(id),
    from_status VARCHAR(32) NOT NULL,
    to_status   VARCHAR(32) NOT NULL,
    changed_by  VARCHAR(64) NOT NULL,
    note        TEXT,
    changed_at  TIMESTAMP NOT NULL
);

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

CREATE TABLE IF NOT EXISTS processed_events (
    saga_id      VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS idempotency_records (
    key           VARCHAR(255) PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    operation     VARCHAR(255) NOT NULL,
    response_body TEXT NOT NULL,
    created_at    TIMESTAMP NOT NULL
);
