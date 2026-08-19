-- V3: Add document processing and fraud detection support
ALTER TABLE claim_documents
    ADD COLUMN IF NOT EXISTS processing_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';

ALTER TABLE claim_documents
    ADD COLUMN IF NOT EXISTS processing_error TEXT;

ALTER TABLE claim_documents
    ADD COLUMN IF NOT EXISTS processed_at TIMESTAMP;

ALTER TABLE claim_documents
    ADD COLUMN IF NOT EXISTS fraud_flag BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_claim_documents_status
    ON claim_documents (claim_id, processing_status);

CREATE INDEX IF NOT EXISTS idx_claim_documents_uploaded
    ON claim_documents (uploaded_at DESC);

-- Enhanced claim_status_history for audit trail
ALTER TABLE claim_status_history
    ADD COLUMN IF NOT EXISTS metadata TEXT;

CREATE INDEX IF NOT EXISTS idx_claim_status_history_claim
    ON claim_status_history (claim_id, changed_at DESC);

