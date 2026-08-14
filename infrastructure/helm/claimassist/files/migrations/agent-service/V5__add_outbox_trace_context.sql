-- V5__add_outbox_trace_context.sql
-- Adds correlation context fields to outbox_events for distributed trace propagation
-- These fields are captured at event creation time so they can be propagated to Kafka headers
-- when publishing (OutboxEventPublisher runs in a scheduled thread with separate MDC)

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(255);

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(255);

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS span_id VARCHAR(255);

-- Create index to help with tracing queries
CREATE INDEX IF NOT EXISTS idx_outbox_correlation_id
    ON outbox_events (correlation_id);

CREATE INDEX IF NOT EXISTS idx_outbox_trace_id
    ON outbox_events (trace_id);

