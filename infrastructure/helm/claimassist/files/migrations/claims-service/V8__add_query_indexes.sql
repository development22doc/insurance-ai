-- V8: Add query indexes for claims-service access patterns.
-- idx_claim_parties_user_id supports ClaimRepository.findAllAccessibleByUser,
-- which filters claim_parties by user_id (the "my claims" listing). The
-- composite primary key (claim_id, user_id) does not lead with user_id.
CREATE INDEX IF NOT EXISTS idx_claim_parties_user_id
    ON claim_parties (user_id, claim_id);

-- idx_claim_saga_status_updated_at supports
-- ClaimSagaOrchestrationRepository.findRecoverableSagas:
-- WHERE status = ? AND updated_at <= ? (equality column first, range second).
CREATE INDEX IF NOT EXISTS idx_claim_saga_status_updated_at
    ON claim_saga_orchestrations (status, updated_at);