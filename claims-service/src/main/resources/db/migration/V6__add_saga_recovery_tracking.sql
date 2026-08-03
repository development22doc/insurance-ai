-- V6__add_saga_recovery_tracking.sql
-- Adds failure recovery and compensation tracking columns to saga orchestration

ALTER TABLE claim_saga_orchestrations
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP;

ALTER TABLE claim_saga_orchestrations
    ADD COLUMN IF NOT EXISTS last_recovery_attempt_at TIMESTAMP;

ALTER TABLE claim_saga_orchestrations
    ADD COLUMN IF NOT EXISTS last_recovery_error TEXT;

ALTER TABLE claim_saga_orchestrations
    ADD COLUMN IF NOT EXISTS recovery_failure_count INTEGER DEFAULT 0;

-- Index for recovery scanning
CREATE INDEX IF NOT EXISTS idx_saga_recovery_status_next_retry
    ON claim_saga_orchestrations (status, next_retry_at)
    WHERE status = 'FAILED' AND next_retry_at IS NOT NULL;

