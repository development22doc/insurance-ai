-- Policy migration reconciliation metadata for source-system traceability.
-- This table is Policy Service owned and intentionally stores only the minimal
-- legacy-to-target identity mapping needed for migration audit and reconciliation.
-- It does not become part of the active Policy domain model.

CREATE TABLE IF NOT EXISTS legacy_customer_policy_id_map (
    legacy_customer_policy_id BIGINT NOT NULL UNIQUE,
    policy_service_policy_id BIGINT NOT NULL UNIQUE,
    source_system VARCHAR(64) NOT NULL DEFAULT 'customer_service',
    legacy_customer_id BIGINT,
    legacy_policy_number VARCHAR(64),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT fk_legacy_customer_policy_policy
        FOREIGN KEY (policy_service_policy_id) REFERENCES policies(id) ON DELETE CASCADE
);

-- UNIQUE on policy_service_policy_id already creates the needed index; no redundant separate index.
