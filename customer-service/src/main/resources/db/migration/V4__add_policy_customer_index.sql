-- V4: Add index on policies.customer_id to support customer policy queries.
-- Supports PolicyRepository.findByCustomerId (plain and paginated) and
-- PolicyRepository.findByIdAndCustomerId (customer_id half), all of which
-- filter policies by customer_id.
CREATE INDEX IF NOT EXISTS idx_policies_customer_id
    ON policies (customer_id);