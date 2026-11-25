-- Stage 9b: evaluations (scorecards) of interviews, and free evaluations of an application.

CREATE TABLE evaluations (
    id             UUID PRIMARY KEY,
    application_id UUID                     NOT NULL,
    candidate_id   UUID                     NOT NULL,
    job_id         UUID                     NOT NULL,
    -- NULL = free evaluation by an administrator or recruiter (not tied to an interview)
    interview_id   UUID,
    evaluator_id   UUID                     NOT NULL,
    status         VARCHAR(10)              NOT NULL,
    recommendation VARCHAR(10),
    -- Average of the competency scores above 0 (0 = not evaluated), one decimal
    overall_score  NUMERIC(2, 1),
    -- Free texts: deleted on erasure (scores and recommendation stay for statistics)
    strengths      VARCHAR(800),
    concerns       VARCHAR(800),
    notes          VARCHAR(1500),
    submitted_at   TIMESTAMP WITH TIME ZONE,
    version        BIGINT                   NOT NULL DEFAULT 0,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by     UUID,
    updated_by     UUID,
    CONSTRAINT fk_evaluations_application FOREIGN KEY (application_id) REFERENCES applications (id),
    CONSTRAINT fk_evaluations_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id),
    CONSTRAINT fk_evaluations_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_evaluations_interview FOREIGN KEY (interview_id) REFERENCES interviews (id),
    CONSTRAINT fk_evaluations_evaluator FOREIGN KEY (evaluator_id) REFERENCES users (id),
    CONSTRAINT fk_evaluations_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_evaluations_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_evaluations_status CHECK (status IN ('PENDING', 'SUBMITTED')),
    CONSTRAINT ck_evaluations_recommendation CHECK (recommendation IS NULL OR recommendation IN ('STRONG_YES', 'YES', 'NO', 'STRONG_NO')),
    CONSTRAINT ck_evaluations_overall CHECK (overall_score IS NULL OR overall_score BETWEEN 1 AND 5),
    CONSTRAINT ck_evaluations_submitted CHECK (status = 'PENDING' OR (submitted_at IS NOT NULL AND recommendation IS NOT NULL))
);

-- One evaluation per interviewer of an interview
CREATE UNIQUE INDEX ux_evaluations_interview_evaluator ON evaluations (interview_id, evaluator_id) WHERE interview_id IS NOT NULL;
CREATE INDEX ix_evaluations_application ON evaluations (application_id);
CREATE INDEX ix_evaluations_candidate ON evaluations (candidate_id);
CREATE INDEX ix_evaluations_evaluator ON evaluations (evaluator_id, status);

CREATE TABLE evaluation_scores (
    evaluation_id UUID         NOT NULL,
    position      INTEGER      NOT NULL,
    competency    VARCHAR(60)  NOT NULL,
    -- 0 = not evaluated (left out of the average), 1–5 otherwise
    score         SMALLINT     NOT NULL,
    comment       VARCHAR(300),
    CONSTRAINT pk_evaluation_scores PRIMARY KEY (evaluation_id, position),
    CONSTRAINT fk_evaluation_scores_evaluation FOREIGN KEY (evaluation_id) REFERENCES evaluations (id) ON DELETE CASCADE,
    CONSTRAINT ck_evaluation_scores_score CHECK (score BETWEEN 0 AND 5)
);

-- A4: every reopening by an administrator (who, when, why); never overwritten. The reason is blanked on erasure.
CREATE TABLE evaluation_reopenings (
    id            UUID PRIMARY KEY,
    evaluation_id UUID                     NOT NULL,
    reopened_by   UUID,
    reason        VARCHAR(500),
    reopened_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_evaluation_reopenings_evaluation FOREIGN KEY (evaluation_id) REFERENCES evaluations (id) ON DELETE CASCADE,
    CONSTRAINT fk_evaluation_reopenings_user FOREIGN KEY (reopened_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX ix_evaluation_reopenings_evaluation ON evaluation_reopenings (evaluation_id);
