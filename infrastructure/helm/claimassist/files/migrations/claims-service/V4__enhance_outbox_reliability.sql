ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMP;

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP;

UPDATE outbox_events
SET next_attempt_at = COALESCE(next_attempt_at, published_at, created_at, CURRENT_TIMESTAMP)
WHERE next_attempt_at IS NULL;

ALTER TABLE outbox_events
    ALTER COLUMN next_attempt_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_status_next_attempt_created
    ON outbox_events (status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS idx_outbox_aggregate_event_type
    ON outbox_events (aggregate_id, event_type);
