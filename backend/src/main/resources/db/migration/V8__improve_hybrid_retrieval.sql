DROP INDEX IF EXISTS evidence_retrieval_content_tsv_idx;

ALTER TABLE evidence_retrieval_chunk
    DROP COLUMN content_tsv;

ALTER TABLE evidence_retrieval_chunk
    ADD COLUMN content_tsv tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('german', COALESCE(locator, '')), 'A') ||
        setweight(to_tsvector('german', COALESCE(content, '')), 'B')
    ) STORED;

CREATE INDEX evidence_retrieval_content_tsv_idx
    ON evidence_retrieval_chunk USING GIN (content_tsv);
