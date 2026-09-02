-- Manual migration to add request_id column to outbox_events table
-- Run this if Flyway migration V11__add_outbox_request_id.sql fails to apply

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS request_id VARCHAR(255);

-- Create index to help with request tracing queries
CREATE INDEX IF NOT EXISTS idx_outbox_request_id
    ON outbox_events (request_id);
