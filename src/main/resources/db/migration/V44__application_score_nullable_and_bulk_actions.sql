-- Stage 8 adjustment: an application without structured data to evaluate (anonymous, CV only, no quick profile)
-- has no score (NULL) instead of 0, so it does not look like a bad match in the pipeline. 0 = data evaluated and
-- no requirement met. Existing anonymous CV-only applications of stage 8a become NULL: anonymous = no session, so
-- created_by is NULL (the auditor fills it with the logged-in candidate); no quick profile.
ALTER TABLE applications ALTER COLUMN score DROP NOT NULL;

UPDATE applications SET score = NULL
WHERE created_by IS NULL AND quick_headline IS NULL;

DELETE FROM application_requirement_matches m
USING applications a
WHERE m.application_id = a.id AND a.score IS NULL;

CREATE INDEX ix_applications_job_score ON applications (job_id, score DESC NULLS LAST, applied_at);

-- Bulk actions of the pipeline (stage 8b, adjustment 1): at most N per user per day, counted in the database so
-- the limit holds with several instances. Only the user, the kind, the number of items and the time.
CREATE TABLE application_bulk_actions (
    id         UUID PRIMARY KEY,
    user_id    UUID                     NOT NULL,
    action     VARCHAR(20)              NOT NULL,
    items      INTEGER                  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_application_bulk_actions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_application_bulk_actions_action CHECK (action IN ('MOVE', 'REJECT', 'ADD_TO_JOB'))
);

CREATE INDEX ix_application_bulk_actions_user_created ON application_bulk_actions (user_id, created_at);
