-- V11: Add indexes for the two most frequent claim read paths.
--
-- Rationale (Phase 4, Section 5 - Database Index Review):
--
-- 1. claim_status_history(claim_id)
--    Backs ClaimStatusHistoryRepository.findByClaimIdOrderByChangedAtAsc, which is
--    invoked on EVERY get_claim_status call (InternalClaimsController / the
--    agent-service get_claim_status tool) and on GET /claims/{id}. claim_status_history
--    is an append-only audit trail (never deleted), so it grows without bound; without
--    this index every status read is a sequential scan over the whole history table.
--    The (claim_id, changed_at) composite keeps the ORDER BY changed_at ASC index-only
--    instead of requiring a separate sort. Selectivity is high (one claim's rows),
--    and writes (append-only inserts) are unaffected because new rows insert at the
--    trailing edge of the index.
--
-- 2. claim_documents(claim_id)
--    Backs ClaimDocumentRepository.findByClaimId, used by
--    InternalClaimsController.getClaimDocuments. The table has no index on the
--    claim_id FK today (only the BIGSERIAL PK), so document listings scan the table.
--    claim_id is highly selective (a claim owns a handful of documents) and the FK
--    write path is unchanged.
--
-- Both indexes are created with IF NOT EXISTS to stay idempotent. They are NEW
-- indexes in a NEW migration - V1/V8 are already applied and are never modified.
CREATE INDEX IF NOT EXISTS idx_claim_status_history_claim_id
    ON claim_status_history (claim_id, changed_at);

CREATE INDEX IF NOT EXISTS idx_claim_documents_claim_id
    ON claim_documents (claim_id);
