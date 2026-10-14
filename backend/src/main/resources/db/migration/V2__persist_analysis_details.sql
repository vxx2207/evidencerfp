ALTER TABLE analysis_question
    ADD COLUMN position INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN citations JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN question_findings JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE finding
    ADD COLUMN position INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN blocks_approval BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE analysis_document (
    analysis_id UUID NOT NULL REFERENCES analysis(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    name VARCHAR(255) NOT NULL,
    media_type VARCHAR(160) NOT NULL,
    chunk_count INTEGER NOT NULL,
    document_date DATE,
    stale BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (analysis_id, position)
);
