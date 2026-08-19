-- V3: Add refresh tokens table for local token management and rotation tracking
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(512) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    issued_at   TIMESTAMP NOT NULL,
    expires_at  TIMESTAMP NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    rotated_to  VARCHAR(512)
);

-- Index for efficient customer-based queries
CREATE INDEX idx_refresh_tokens_customer_id ON refresh_tokens(customer_id);

-- Index for cleaning up expired tokens
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

-- Index for token lookup
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);

