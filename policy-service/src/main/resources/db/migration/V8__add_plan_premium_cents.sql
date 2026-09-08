-- V8__add_plan_premium_cents.sql
-- Add premium_cents column to plans table for authoritative pricing
-- This is the authoritative source for initial policy purchase premium

ALTER TABLE plans
  ADD COLUMN IF NOT EXISTS premium_cents BIGINT;

-- Add check constraint to ensure premium is non-negative when present
ALTER TABLE plans
  ADD CONSTRAINT chk_premium_cents_non_negative
  CHECK (premium_cents IS NULL OR premium_cents >= 0);

-- Add comment for documentation
COMMENT ON COLUMN plans.premium_cents IS 'Authoritative premium amount in cents for this plan. Used for initial policy purchase PaymentIntent amount.';
