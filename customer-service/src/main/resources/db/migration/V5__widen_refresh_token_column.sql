-- V5: Widen refresh token columns for Keycloak-issued JWTs.
--
-- Phase 2: the local refresh_tokens table now stores the ACTUAL Keycloak-issued
-- refresh token value (not a locally generated random token). Keycloak refresh
-- tokens are signed JWTs that routinely exceed the previous VARCHAR(512) limit,
-- which would otherwise truncate/fail persistence. TEXT is unbounded, matching
-- the Keycloak token size.
ALTER TABLE refresh_tokens ALTER COLUMN token TYPE TEXT;
ALTER TABLE refresh_tokens ALTER COLUMN rotated_to TYPE TEXT;