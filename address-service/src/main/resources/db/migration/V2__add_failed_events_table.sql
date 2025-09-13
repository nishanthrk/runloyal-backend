-- Failed events table for dead letter queue functionality
CREATE TABLE failed_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) UNIQUE NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    partition INTEGER NOT NULL,
    "offset" BIGINT NOT NULL,
    message TEXT,
    error_message TEXT,
    error_stack_trace TEXT,
    failed_at TIMESTAMPTZ DEFAULT now(),
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_retry_at TIMESTAMPTZ,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING'
);

-- Indexes for efficient querying
CREATE INDEX idx_failed_events_status ON failed_events (status);
CREATE INDEX idx_failed_events_event_type ON failed_events (event_type);
CREATE INDEX idx_failed_events_topic ON failed_events (topic);
CREATE INDEX idx_failed_events_failed_at ON failed_events (failed_at);
CREATE INDEX idx_failed_events_retry_count ON failed_events (retry_count);
CREATE INDEX idx_failed_events_pending_retry ON failed_events (status, retry_count, failed_at) WHERE status = 'PENDING';

-- Add constraint for status values
ALTER TABLE failed_events ADD CONSTRAINT chk_failed_events_status 
    CHECK (status IN ('PENDING', 'RETRYING', 'RESOLVED', 'PERMANENTLY_FAILED'));
