-- V6__add_outbox_request_id.sql
-- Adds request_id field to outbox_events for request propagation
-- This field is captured at event creation time so it can be propagated to Kafka headers
-- when publishing (OutboxEventPublisher runs in a scheduled thread with separate MDC)

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS request_id VARCHAR(255);

-- Create index to help with request tracing queries
CREATE INDEX IF NOT EXISTS idx_outbox_request_id
    ON outbox_events (request_id);
