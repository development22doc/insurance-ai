CREATE TABLE IF NOT EXISTS customers (
    id                 BIGSERIAL PRIMARY KEY,
    username           VARCHAR(255) NOT NULL UNIQUE,
    password           VARCHAR(255) NOT NULL,
    full_name          VARCHAR(255) NOT NULL,
    stripe_customer_id VARCHAR(255),
    kyc_status         VARCHAR(32) NOT NULL DEFAULT 'PENDING'
);

CREATE TABLE IF NOT EXISTS coverage_plans (
    id                   BIGSERIAL PRIMARY KEY,
    name                 VARCHAR(64) NOT NULL,
    product_type         VARCHAR(32) NOT NULL,
    annual_premium_cents BIGINT NOT NULL,
    deductible_cents     BIGINT NOT NULL,
    coverage_limit_cents BIGINT NOT NULL,
    stripe_price_id      VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS policies (
    id                     BIGSERIAL PRIMARY KEY,
    customer_id            BIGINT NOT NULL REFERENCES customers(id),
    coverage_plan_id       BIGINT NOT NULL REFERENCES coverage_plans(id),
    policy_number          VARCHAR(64) NOT NULL UNIQUE,
    status                 VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    effective_date         TIMESTAMP NOT NULL,
    renewal_date           TIMESTAMP,
    stripe_subscription_id VARCHAR(255)
);

-- A few starter coverage plans so the platform is usable out of the box.
INSERT INTO coverage_plans (name, product_type, annual_premium_cents, deductible_cents, coverage_limit_cents)
VALUES
    ('Basic', 'AUTO', 60000, 100000, 2500000),
    ('Standard', 'AUTO', 95000, 50000, 5000000),
    ('Comprehensive', 'AUTO', 140000, 25000, 10000000);
