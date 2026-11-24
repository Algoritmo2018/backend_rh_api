-- Screening questions asked in the application form of a job
CREATE TABLE screening_questions (
    id              UUID PRIMARY KEY,
    job_id          UUID                     NOT NULL,
    position        INTEGER                  NOT NULL,
    question        VARCHAR(200)             NOT NULL,
    type            VARCHAR(20)              NOT NULL,
    options         TEXT[]                   NOT NULL DEFAULT '{}',
    required        BOOLEAN                  NOT NULL,
    -- A different answer marks the candidate as eliminated (never shown to candidates)
    eliminatory     BOOLEAN                  NOT NULL,
    expected_answer VARCHAR(100),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT fk_screening_questions_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_screening_questions_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_screening_questions_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_screening_questions_type CHECK (type IN ('YES_NO', 'SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TEXT')),
    CONSTRAINT ck_screening_questions_eliminatory CHECK (
        NOT eliminatory OR (type IN ('YES_NO', 'SINGLE_CHOICE') AND expected_answer IS NOT NULL))
);

CREATE INDEX ix_screening_questions_job_id ON screening_questions (job_id, position);
