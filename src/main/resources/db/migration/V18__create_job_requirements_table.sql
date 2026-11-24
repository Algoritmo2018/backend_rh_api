-- Requirements of a job, compared with each candidate profile to compute the match score
CREATE TABLE job_requirements (
    id          UUID PRIMARY KEY,
    job_id      UUID                     NOT NULL,
    position    INTEGER                  NOT NULL,
    description VARCHAR(160)             NOT NULL,
    type        VARCHAR(10)              NOT NULL,
    weight      INTEGER                  NOT NULL,
    category    VARCHAR(10)              NOT NULL,
    -- Value compared with the profile (skill name, license category, language, education level, province)
    value       VARCHAR(120),
    -- experience: minimum years; language: minimum level (1–5)
    min_value   INTEGER,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by  UUID,
    updated_by  UUID,
    CONSTRAINT fk_job_requirements_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_requirements_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_job_requirements_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_job_requirements_type CHECK (type IN ('MANDATORY', 'DESIRABLE')),
    CONSTRAINT ck_job_requirements_weight CHECK (weight BETWEEN 1 AND 5),
    CONSTRAINT ck_job_requirements_category CHECK (category IN ('SKILL', 'EXPERIENCE', 'EDUCATION', 'LANGUAGE', 'LICENSE', 'LOCATION')),
    CONSTRAINT ck_job_requirements_value CHECK (category = 'EXPERIENCE' OR value IS NOT NULL),
    CONSTRAINT ck_job_requirements_min_value CHECK (min_value IS NULL OR min_value BETWEEN 0 AND 40)
);

CREATE INDEX ix_job_requirements_job_id ON job_requirements (job_id, position);
