CREATE TABLE analysis_evidence_passage (
    analysis_id UUID NOT NULL REFERENCES analysis(id) ON DELETE CASCADE,
    document_position INTEGER NOT NULL,
    position INTEGER NOT NULL,
    chunk_id UUID NOT NULL,
    locator VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    PRIMARY KEY (analysis_id, chunk_id),
    CONSTRAINT fk_passage_document
        FOREIGN KEY (analysis_id, document_position)
        REFERENCES analysis_document(analysis_id, position)
        ON DELETE CASCADE
);

CREATE INDEX idx_analysis_evidence_passage_document
    ON analysis_evidence_passage(analysis_id, document_position, position);
