-- Add revoked access tokens table (for JWT token blacklisting)
CREATE TABLE revoked_access_tokens (
    jti UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    revoked_at TIMESTAMPTZ DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

-- Indexes for revoked access tokens
CREATE INDEX idx_revoked_tokens_user_id ON revoked_access_tokens (user_id);
CREATE INDEX idx_revoked_tokens_expires_at ON revoked_access_tokens (expires_at);
