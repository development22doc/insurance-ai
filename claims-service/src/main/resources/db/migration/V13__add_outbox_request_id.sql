-- Adds request_id field to outbox_events for request propagation.
-- This field is captured at event creation time so it can be propagated to
-- Kafka headers when the scheduled publisher runs without request MDC.

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS request_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_outbox_request_id
    ON outbox_events (request_id);
