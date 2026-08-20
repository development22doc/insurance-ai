-- Phase 2 Task 10.1: Optimistic locking for Claim.
-- Adds a version column that Hibernate/JPA @Version uses to detect concurrent
-- (lost-update) writes to the same claim. Existing rows receive version 0 via
-- the NOT NULL DEFAULT; new rows are initialized by Hibernate on insert.
-- Non-destructive: no drop/recreate, no existing index changes.
ALTER TABLE claims ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;