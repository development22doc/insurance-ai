-- Phase 14 Task 14.1: Align claims.version column type with the entity.
-- The Claim entity maps @Version as Long (bigint) and V9 declared BIGINT, but the
-- column was created earlier as INTEGER and V9's ADD COLUMN IF NOT EXISTS was a
-- no-op, causing local-profile schema validation to fail (found int4, expecting
-- bigint). This alters the existing column type non-destructively (no drop/recreate).
ALTER TABLE claims ALTER COLUMN version TYPE BIGINT;