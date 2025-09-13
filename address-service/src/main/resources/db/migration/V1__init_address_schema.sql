-- Addresses table
CREATE TABLE addresses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    line1 VARCHAR(255) NOT NULL,
    line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    postal_code VARCHAR(20),
    is_primary BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Event processing table to track processed events (for idempotency)
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ DEFAULT now()
);

-- Indexes
CREATE INDEX idx_addresses_user_id ON addresses (user_id);
CREATE INDEX idx_addresses_primary ON addresses (user_id, is_primary) WHERE is_primary = true;
CREATE INDEX idx_processed_events_type ON processed_events (event_type, processed_at);

-- Constraint to ensure only one primary address per user
CREATE UNIQUE INDEX ux_user_primary_address ON addresses (user_id) WHERE is_primary = true;