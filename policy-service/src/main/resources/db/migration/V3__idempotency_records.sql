-- V3__idempotency_records.sql
-- Add idempotency_records table for idempotent POST operations

CREATE TABLE IF NOT EXISTS idempotency_records (
  key VARCHAR(128) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  operation VARCHAR(128) NOT NULL,
  response_body TEXT NOT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);
