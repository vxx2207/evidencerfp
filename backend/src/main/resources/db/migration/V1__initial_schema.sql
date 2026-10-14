CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE analysis (
    id UUID PRIMARY KEY,
    customer_name VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    synthetic BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE TABLE analysis_question (
    analysis_id UUID NOT NULL REFERENCES analysis(id) ON DELETE CASCADE,
    id VARCHAR(120) NOT NULL,
    question TEXT NOT NULL,
    category VARCHAR(160),
    answer TEXT,
    answer_status VARCHAR(32) NOT NULL,
    review_status VARCHAR(32) NOT NULL,
    internal_clarification TEXT,
    PRIMARY KEY (analysis_id, id)
);

CREATE TABLE evidence_chunk (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analysis(id) ON DELETE CASCADE,
    document_name VARCHAR(255) NOT NULL,
    locator VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    document_date DATE,
    embedding vector(1024)
);

CREATE INDEX evidence_chunk_analysis_idx ON evidence_chunk(analysis_id);
CREATE INDEX evidence_chunk_embedding_hnsw_idx
    ON evidence_chunk USING hnsw (embedding vector_cosine_ops);

CREATE TABLE citation (
    analysis_id UUID NOT NULL,
    question_id VARCHAR(120) NOT NULL,
    chunk_id UUID NOT NULL REFERENCES evidence_chunk(id) ON DELETE CASCADE,
    quoted_text TEXT NOT NULL,
    PRIMARY KEY (analysis_id, question_id, chunk_id),
    FOREIGN KEY (analysis_id, question_id)
        REFERENCES analysis_question(analysis_id, id) ON DELETE CASCADE
);

CREATE TABLE finding (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analysis(id) ON DELETE CASCADE,
    question_id VARCHAR(120),
    type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    rule_id VARCHAR(64),
    title VARCHAR(240) NOT NULL,
    description TEXT NOT NULL,
    affected_object VARCHAR(240),
    recommendation TEXT NOT NULL
);

CREATE INDEX finding_analysis_idx ON finding(analysis_id);

-- Kurzlebiger Arbeitsindex des Production-EvidenceRetriever. Er ist absichtlich nicht
-- an analysis gekoppelt, weil Retrieval bereits während der noch laufenden Analyse erfolgt.
CREATE TABLE evidence_retrieval_chunk (
    session_id UUID NOT NULL,
    chunk_id UUID NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(160) NOT NULL,
    locator VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    document_date DATE,
    embedding vector(1024) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL DEFAULT now() + INTERVAL '1 day',
    PRIMARY KEY (session_id, chunk_id)
);

CREATE INDEX evidence_retrieval_embedding_hnsw_idx
    ON evidence_retrieval_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX evidence_retrieval_expiry_idx ON evidence_retrieval_chunk(expires_at);
