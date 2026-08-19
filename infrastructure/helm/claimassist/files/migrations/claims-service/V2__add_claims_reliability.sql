-- V2: Add reliability and tracking columns for claims processing
ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS locked_by VARCHAR(255);

ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_claims_policy_status
    ON claims (policy_id, status);

CREATE INDEX IF NOT EXISTS idx_claims_updated_at
    ON claims (updated_at DESC);

