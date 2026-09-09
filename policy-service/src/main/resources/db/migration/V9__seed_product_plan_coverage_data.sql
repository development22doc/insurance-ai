-- V9__seed_product_plan_coverage_data.sql
-- CRITICAL WARNING: This migration creates SYNTHETIC seed data for LOCAL DEVELOPMENT ONLY
-- DO NOT run this migration in production, dev, or any remote database environments
-- DO NOT run this migration against OCI PostgreSQL or any remote database
-- This data is designed to match Customer Service CoveragePlans for testing the purchase flow
--
-- Synthetic catalog structure:
-- - Product: AUTO (synthetic test product)
-- - Plans: BASIC, STANDARD, COMPREHENSIVE (synthetic test plans matching Customer CoveragePlan names)
-- - Coverage: COMPREHENSIVE (simplified coverage model for testing)
--
-- SAFETY REQUIREMENTS:
-- - This migration should ONLY run against a genuinely local PostgreSQL database (localhost)
-- - It MUST NOT run against OCI PostgreSQL, dev databases, or production databases
-- - Flyway must be explicitly enabled via FLYWAY_ENABLED=true environment variable
-- - The datasource must point to a local database, not a remote OCI database
-- - The dev and local-k8s profiles have empty migration locations to prevent accidental execution
-- - Only the local profile can run this migration when FLYWAY_ENABLED=true
--
-- For production, use actual product catalog data through proper migration or admin tools

-- Insert AUTO product
INSERT INTO products (code, name, created_at)
VALUES ('AUTO', 'Auto Insurance', NOW())
ON CONFLICT (code) DO NOTHING;

-- Insert BASIC plan (matches Customer "Basic" CoveragePlan ID 1)
-- We use a CTE to get the product ID safely
WITH auto_product AS (
    SELECT id FROM products WHERE code = 'AUTO' LIMIT 1
)
INSERT INTO plans (product_id, code, name, active, deductible_cents, coverage_limit_cents, premium_cents, created_at)
SELECT id, 'BASIC', 'Basic', true, 100000, 2500000, 60000, NOW()
FROM auto_product
ON CONFLICT (product_id, code) DO NOTHING;

-- Insert STANDARD plan (matches Customer "Standard" CoveragePlan ID 2)
WITH auto_product AS (
    SELECT id FROM products WHERE code = 'AUTO' LIMIT 1
)
INSERT INTO plans (product_id, code, name, active, deductible_cents, coverage_limit_cents, premium_cents, created_at)
SELECT id, 'STANDARD', 'Standard', true, 50000, 5000000, 95000, NOW()
FROM auto_product
ON CONFLICT (product_id, code) DO NOTHING;

-- Insert COMPREHENSIVE plan (matches Customer "Comprehensive" CoveragePlan ID 3)
WITH auto_product AS (
    SELECT id FROM products WHERE code = 'AUTO' LIMIT 1
)
INSERT INTO plans (product_id, code, name, active, deductible_cents, coverage_limit_cents, premium_cents, created_at)
SELECT id, 'COMPREHENSIVE', 'Comprehensive', true, 25000, 10000000, 140000, NOW()
FROM auto_product
ON CONFLICT (product_id, code) DO NOTHING;

-- Insert COMPREHENSIVE coverage for each plan
-- This is a simplified coverage model for testing
INSERT INTO coverages (plan_id, code, name, limit_cents, deductible_cents, created_at)
SELECT p.id, 'COMPREHENSIVE', 'Comprehensive Coverage', p.coverage_limit_cents, p.deductible_cents, NOW()
FROM plans p
JOIN products pr ON p.product_id = pr.id
WHERE pr.code = 'AUTO'
ON CONFLICT (plan_id, code) DO NOTHING;
