-- V6__add_policy_version_column.sql
-- Ensure the optimistic-locking version column exists for the Policy JPA entity.
-- This is a compatibility fix for the schema/runtime contract and is intentionally
-- minimal: it adds the missing column without changing the active policy domain.
ALTER TABLE policies
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
