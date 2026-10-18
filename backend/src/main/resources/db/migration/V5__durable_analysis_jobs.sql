ALTER TABLE analysis_question
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT analysis_question_revision_non_negative CHECK (revision >= 0);

CREATE TABLE analysis_job (
    analysis_id UUID PRIMARY KEY REFERENCES analysis(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    progress_percent INTEGER NOT NULL DEFAULT 0,
    attempt INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 2,
    lease_owner VARCHAR(160),
    lease_until TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    failure_code VARCHAR(80),
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    CONSTRAINT analysis_job_progress_range CHECK (progress_percent BETWEEN 0 AND 100),
    CONSTRAINT analysis_job_attempt_range CHECK (attempt BETWEEN 0 AND max_attempts),
    CONSTRAINT analysis_job_lease_pair CHECK (
        (lease_owner IS NULL AND lease_until IS NULL)
        OR (lease_owner IS NOT NULL AND lease_until IS NOT NULL)
    )
);
CREATE INDEX idx_analysis_job_runnable
    ON analysis_job(status, lease_until, created_at)
    WHERE cancel_requested = FALSE;

CREATE TABLE analysis_job_upload (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analysis_job(analysis_id) ON DELETE CASCADE,
    kind VARCHAR(32) NOT NULL,
    position INTEGER NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(160),
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    CONSTRAINT analysis_job_upload_size_non_negative CHECK (size_bytes >= 0),
    CONSTRAINT analysis_job_upload_position_non_negative CHECK (position >= 0),
    UNIQUE (analysis_id, kind, position),
    UNIQUE (analysis_id, storage_path)
);

CREATE INDEX idx_analysis_job_upload_analysis
    ON analysis_job_upload(analysis_id, kind, position);

CREATE TABLE analysis_idempotency (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    request_hash CHAR(64) NOT NULL,
    analysis_id UUID NOT NULL REFERENCES analysis(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL
);
