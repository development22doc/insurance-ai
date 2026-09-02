-- Mirrors claims-service/src/main/resources/db/migration/V13__add_outbox_request_id.sql.
ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS request_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_outbox_request_id
    ON outbox_events (request_id);
