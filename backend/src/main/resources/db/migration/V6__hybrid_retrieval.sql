ALTER TABLE evidence_retrieval_chunk
    ADD COLUMN content_tsv tsvector
    GENERATED ALWAYS AS (to_tsvector('german', COALESCE(content, ''))) STORED;

CREATE INDEX evidence_retrieval_content_tsv_idx
    ON evidence_retrieval_chunk USING GIN (content_tsv);
