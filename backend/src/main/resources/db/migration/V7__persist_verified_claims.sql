ALTER TABLE analysis_question
    ADD COLUMN claims jsonb NOT NULL DEFAULT '[]'::jsonb;
