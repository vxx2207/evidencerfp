UPDATE analysis_job
SET attempt = LEAST(attempt, 2),
    max_attempts = LEAST(2, GREATEST(1, max_attempts));

ALTER TABLE analysis_job
    ADD CONSTRAINT analysis_job_max_attempt_cap
        CHECK (max_attempts BETWEEN 1 AND 2);
