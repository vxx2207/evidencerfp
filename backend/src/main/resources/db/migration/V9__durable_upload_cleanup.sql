ALTER TABLE analysis ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE TABLE analysis_cleanup_task (
    analysis_id UUID PRIMARY KEY,
    reason VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_owner VARCHAR(200),
    lease_until TIMESTAMPTZ,
    last_error_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT analysis_cleanup_status CHECK (status IN ('PENDING', 'PROCESSING')),
    CONSTRAINT analysis_cleanup_attempt_non_negative CHECK (attempt_count >= 0)
);

CREATE INDEX idx_analysis_cleanup_pending
    ON analysis_cleanup_task(next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE TABLE application_seed (
    seed_key VARCHAR(100) PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL
);
