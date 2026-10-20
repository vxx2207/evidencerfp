ALTER TABLE analysis
    ADD COLUMN retrieval_version VARCHAR(160) NOT NULL
        DEFAULT 'legacy-unrecorded',
    ADD COLUMN prompt_version VARCHAR(160) NOT NULL
        DEFAULT 'legacy-unrecorded',
    ADD COLUMN embedding_model VARCHAR(240) NOT NULL
        DEFAULT 'legacy-unrecorded',
    ADD COLUMN chat_model VARCHAR(240) NOT NULL
        DEFAULT 'legacy-unrecorded';

COMMENT ON COLUMN analysis.retrieval_version IS
    'Retrieval-Version, die beim Analyse-Lauf tatsächlich verwendet wurde';
COMMENT ON COLUMN analysis.prompt_version IS
    'Prompt-Version, die beim Analyse-Lauf tatsächlich verwendet wurde';
COMMENT ON COLUMN analysis.embedding_model IS
    'Embedding-Modell, das beim Analyse-Lauf tatsächlich verwendet wurde';
COMMENT ON COLUMN analysis.chat_model IS
    'Chat-Modell, das beim Analyse-Lauf tatsächlich verwendet wurde';
