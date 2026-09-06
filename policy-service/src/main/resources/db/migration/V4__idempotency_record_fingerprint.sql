-- V4__idempotency_record_fingerprint.sql
-- Add fingerprint column to idempotency_records to store deterministic request fingerprints
ALTER TABLE idempotency_records
    ADD COLUMN IF NOT EXISTS fingerprint VARCHAR(128);

-- No NOT NULL constraint to preserve backward compatibility with existing records.
