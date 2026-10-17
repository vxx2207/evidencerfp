CREATE TABLE analysis_review_event (
    ordinal BIGINT GENERATED ALWAYS AS IDENTITY,
    id UUID NOT NULL UNIQUE,
    analysis_id UUID NOT NULL REFERENCES analysis(id) ON DELETE CASCADE,
    question_id VARCHAR(120) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    provenance VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    before_answer TEXT,
    after_answer TEXT,
    before_review_status VARCHAR(32),
    after_review_status VARCHAR(32) NOT NULL,
    PRIMARY KEY (ordinal)
);

CREATE INDEX idx_analysis_review_event_timeline
    ON analysis_review_event(analysis_id, ordinal);

-- Beim Upgrade erhalten bereits vorhandene Antwortentwürfe ebenfalls einen Ursprungsnachweis.
INSERT INTO analysis_review_event
    (id, analysis_id, question_id, actor, action, provenance, occurred_at,
     before_answer, after_answer, before_review_status, after_review_status)
SELECT md5(a.id::text || ':' || q.id || ':GENERATED')::uuid,
       a.id, q.id, 'ANALYSIS_ENGINE', 'GENERATED', 'GENERATED',
       COALESCE(a.completed_at, a.created_at), NULL, q.answer, NULL, q.review_status
FROM analysis a
JOIN analysis_question q ON q.analysis_id = a.id
WHERE a.status = 'COMPLETED'
ORDER BY a.created_at, a.id, q.position, q.id;
