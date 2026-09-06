-- V2__stripe_payment_and_processed_events.sql
-- Add stripe payment intent persistence and processed events table

-- 1) Add stripe_payment_intent_id to policies
ALTER TABLE policies
  ADD COLUMN IF NOT EXISTS stripe_payment_intent_id VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS uq_policies_stripe_payment_intent ON policies(stripe_payment_intent_id);

-- 2) Create processed_stripe_events for webhook idempotency
CREATE TABLE IF NOT EXISTS processed_stripe_events (
  event_id VARCHAR(128) PRIMARY KEY,
  event_type VARCHAR(128) NOT NULL,
  processed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
  result TEXT
);

-- 3) Add a sequence for policy number generation
CREATE SEQUENCE IF NOT EXISTS policy_number_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

-- End of migration
