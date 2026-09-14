CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS policy_contracts (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    customer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    policy_number VARCHAR(64) NOT NULL UNIQUE,
    current_policy_period_id BIGINT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS policy_periods (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    policy_contract_id BIGINT NOT NULL,
    previous_policy_period_id BIGINT,
    plan_id BIGINT NOT NULL,
    renewal_sequence INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    effective_date TIMESTAMPTZ NOT NULL,
    expiration_date TIMESTAMPTZ NOT NULL,
    renewal_date TIMESTAMPTZ,
    activated_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_policy_period_dates CHECK (effective_date < expiration_date),
    CONSTRAINT uk_policy_periods_contract_sequence UNIQUE (policy_contract_id, renewal_sequence)
);

ALTER TABLE policy_periods
    ADD CONSTRAINT fk_policy_period_contract
    FOREIGN KEY (policy_contract_id)
    REFERENCES policy_contracts(id)
    ON DELETE RESTRICT;

ALTER TABLE policy_periods
    ADD CONSTRAINT fk_policy_period_previous_policy_period
    FOREIGN KEY (previous_policy_period_id)
    REFERENCES policy_periods(id)
    ON DELETE RESTRICT;

ALTER TABLE policy_contracts
    ADD CONSTRAINT fk_policy_contract_current_policy_period
    FOREIGN KEY (current_policy_period_id)
    REFERENCES policy_periods(id)
    ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_policy_contracts_customer_id ON policy_contracts(customer_id);
CREATE INDEX IF NOT EXISTS idx_policy_contracts_current_policy_period_id ON policy_contracts(current_policy_period_id);
CREATE INDEX IF NOT EXISTS idx_policy_periods_contract_id ON policy_periods(policy_contract_id);
CREATE INDEX IF NOT EXISTS idx_policy_periods_previous_policy_period_id ON policy_periods(previous_policy_period_id);
CREATE INDEX IF NOT EXISTS idx_policy_periods_contract_effective_date ON policy_periods(policy_contract_id, effective_date);
CREATE INDEX IF NOT EXISTS idx_policy_periods_contract_expiration_date ON policy_periods(policy_contract_id, expiration_date);

ALTER TABLE policy_periods
    ADD CONSTRAINT excl_policy_periods_same_contract_no_overlap
    EXCLUDE USING gist (
        policy_contract_id WITH =,
        tstzrange(effective_date, expiration_date, '[)') WITH &&
    );

CREATE TABLE IF NOT EXISTS policy_legacy_id_map (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    legacy_source VARCHAR(64) NOT NULL,
    legacy_policy_id BIGINT NOT NULL,
    policy_contract_id BIGINT NOT NULL,
    legacy_record_type VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_policy_legacy_id_map_source_policy_id UNIQUE (legacy_source, legacy_policy_id)
);

ALTER TABLE policy_legacy_id_map
    ADD CONSTRAINT fk_policy_legacy_id_map_policy_contract
    FOREIGN KEY (policy_contract_id)
    REFERENCES policy_contracts(id)
    ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_policy_legacy_id_map_policy_contract_id ON policy_legacy_id_map(policy_contract_id);

CREATE TABLE IF NOT EXISTS purchases (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    customer_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    customer_policy_id BIGINT,
    purchase_type VARCHAR(32),
    policy_contract_id BIGINT,
    source_policy_period_id BIGINT,
    target_policy_period_id BIGINT,
    amount_cents BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    provider VARCHAR(64),
    provider_session_id VARCHAR(128),
    provider_payment_intent_id VARCHAR(128),
    initiated_at TIMESTAMPTZ NOT NULL,
    paid_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_purchases_purchase_type CHECK (purchase_type IS NULL OR purchase_type IN ('NEW_POLICY', 'RENEWAL')),
    CONSTRAINT chk_purchases_status CHECK (status IN ('PENDING_PAYMENT', 'PAYMENT_PROCESSING', 'PAYMENT_FAILED', 'PAID', 'CANCELLED', 'EXPIRED'))
);

ALTER TABLE purchases
    ADD COLUMN IF NOT EXISTS purchase_type VARCHAR(32);

ALTER TABLE purchases
    ADD COLUMN IF NOT EXISTS policy_contract_id BIGINT;

ALTER TABLE purchases
    ADD COLUMN IF NOT EXISTS source_policy_period_id BIGINT;

ALTER TABLE purchases
    ADD COLUMN IF NOT EXISTS target_policy_period_id BIGINT;

ALTER TABLE purchases
    ADD CONSTRAINT fk_purchases_policy_contract
    FOREIGN KEY (policy_contract_id)
    REFERENCES policy_contracts(id)
    ON DELETE RESTRICT;

ALTER TABLE purchases
    ADD CONSTRAINT fk_purchases_source_policy_period
    FOREIGN KEY (source_policy_period_id)
    REFERENCES policy_periods(id)
    ON DELETE RESTRICT;

ALTER TABLE purchases
    ADD CONSTRAINT fk_purchases_target_policy_period
    FOREIGN KEY (target_policy_period_id)
    REFERENCES policy_periods(id)
    ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_purchases_customer_id ON purchases(customer_id);
CREATE INDEX IF NOT EXISTS idx_purchases_policy_contract_id ON purchases(policy_contract_id);
CREATE INDEX IF NOT EXISTS idx_purchases_source_policy_period_id ON purchases(source_policy_period_id);
CREATE INDEX IF NOT EXISTS idx_purchases_target_policy_period_id ON purchases(target_policy_period_id);
CREATE INDEX IF NOT EXISTS idx_purchases_status ON purchases(status);

CREATE UNIQUE INDEX IF NOT EXISTS idx_purchases_active_renewal_source_period
ON purchases(source_policy_period_id)
WHERE purchase_type = 'RENEWAL' AND status IN ('PENDING_PAYMENT', 'PAYMENT_PROCESSING');

CREATE TABLE IF NOT EXISTS payment_events (
    id BIGSERIAL PRIMARY KEY,
    purchase_id BIGINT NOT NULL,
    provider_event_id VARCHAR(128) NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    event_status VARCHAR(32) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE payment_events
    ADD CONSTRAINT fk_payment_events_purchase
    FOREIGN KEY (purchase_id)
    REFERENCES purchases(id)
    ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_payment_events_purchase_id ON payment_events(purchase_id);
