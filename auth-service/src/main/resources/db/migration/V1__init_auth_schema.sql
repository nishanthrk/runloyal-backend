-- Auth credentials table (only authentication data)
CREATE TABLE auth_credentials (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL, -- Reference to User Service user ID
    password_hash VARCHAR(255),
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Refresh tokens table
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL, -- Reference to User Service user ID
    client_id VARCHAR(100),
    device_info TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN DEFAULT false,
    replaced_by UUID,
    metadata JSONB
);

-- OAuth provider tokens (for Auth Service to call external providers)
CREATE TABLE oauth_provider_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL, -- Reference to User Service user ID
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(200) NOT NULL,
    access_token TEXT,
    refresh_token TEXT,
    token_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Indexes
CREATE UNIQUE INDEX ux_oauth_provider_user ON oauth_provider_tokens (provider, provider_user_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
CREATE INDEX idx_auth_credentials_user_id ON auth_credentials (user_id);
CREATE INDEX idx_oauth_tokens_user_id ON oauth_provider_tokens (user_id);