-- V1__init_policy_schema.sql
-- Initial schema for Policy Service (claimassist_policy)

-- products: product catalog
CREATE TABLE IF NOT EXISTS products (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

-- plans: plan belongs to product
CREATE TABLE IF NOT EXISTS plans (
  id BIGSERIAL PRIMARY KEY,
  product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(255) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true,
  deductible_cents BIGINT,
  coverage_limit_cents BIGINT,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT uq_plans_product_code UNIQUE (product_id, code)
);
CREATE INDEX IF NOT EXISTS idx_plans_product_id ON plans(product_id);

-- coverages: optional detailed coverages tied to a plan (phase 2 foundation)
CREATE TABLE IF NOT EXISTS coverages (
  id BIGSERIAL PRIMARY KEY,
  plan_id BIGINT NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(255) NOT NULL,
  limit_cents BIGINT,
  deductible_cents BIGINT,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT uq_coverage_plan_code UNIQUE (plan_id, code)
);
CREATE INDEX IF NOT EXISTS idx_coverages_plan_id ON coverages(plan_id);

-- policies: issued policy record. customer_id is stored as a plain value (no FK)
CREATE TABLE IF NOT EXISTS policies (
  id BIGSERIAL PRIMARY KEY,
  policy_number VARCHAR(64) NOT NULL UNIQUE,
  customer_id BIGINT NOT NULL,
  coverage_plan_id BIGINT NOT NULL REFERENCES plans(id) ON DELETE RESTRICT,
  status VARCHAR(32) NOT NULL,
  effective_date TIMESTAMP WITHOUT TIME ZONE,
  renewal_date TIMESTAMP WITHOUT TIME ZONE,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_policies_customer_id ON policies(customer_id);

-- policy_version: snapshot of policy at a point in time
CREATE TABLE IF NOT EXISTS policy_version (
  id BIGSERIAL PRIMARY KEY,
  policy_id BIGINT NOT NULL REFERENCES policies(id) ON DELETE CASCADE,
  version_number INTEGER NOT NULL,
  plan_id BIGINT NOT NULL REFERENCES plans(id) ON DELETE RESTRICT,
  premium_cents BIGINT,
  deductible_cents BIGINT,
  coverage_limit_cents BIGINT,
  effective_from TIMESTAMP WITHOUT TIME ZONE,
  effective_to TIMESTAMP WITHOUT TIME ZONE,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT uq_policy_version_unique UNIQUE (policy_id, version_number)
);

-- policy_version_coverage: map version -> coverage snapshot
CREATE TABLE IF NOT EXISTS policy_version_coverage (
  id BIGSERIAL PRIMARY KEY,
  policy_version_id BIGINT NOT NULL REFERENCES policy_version(id) ON DELETE CASCADE,
  coverage_id BIGINT NOT NULL REFERENCES coverages(id) ON DELETE RESTRICT,
  limit_cents BIGINT,
  deductible_cents BIGINT,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT uq_policy_version_coverage_unique UNIQUE (policy_version_id, coverage_id)
);
-- Unique constraint above creates an index on (policy_version_id, coverage_id); no separate idx_pvc_policy_version needed.

-- Prevent accidental FK to other service DBs; this DB owns policy domain only.

COMMENT ON TABLE policies IS 'Policy Service owned table: customer_id is plain value only (no FK to customer_service)';

-- End of migration
