-- Keycloak cutover.
--
-- `password` is no longer written or read by the application - Keycloak is
-- now the sole holder of credentials. We deliberately do NOT drop the column
-- in this release: existing rows keep their bcrypt hash as a rollback safety
-- net (see docs/keycloak-migration-plan.md), and a later cleanup migration
-- can drop it once the Keycloak cutover has been running in production for a
-- full release cycle without incident.
ALTER TABLE customers ALTER COLUMN password DROP NOT NULL;

-- Keycloak's own user id (UUID) for this customer, populated by AuthController
-- at signup time once the corresponding Keycloak user has been created.
ALTER TABLE customers ADD COLUMN IF NOT EXISTS keycloak_id VARCHAR(64) UNIQUE;
