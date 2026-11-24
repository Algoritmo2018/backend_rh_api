-- Jobs (vagas). Never deleted: "closed" is the archive.
CREATE TABLE jobs (
    id                   UUID PRIMARY KEY,
    -- Public URL /vagas/{slug}; fixed once the job is published
    slug                 VARCHAR(80)              NOT NULL,
    title                VARCHAR(90)              NOT NULL,
    client_id            UUID                     NOT NULL,
    is_confidential      BOOLEAN                  NOT NULL DEFAULT FALSE,
    area                 VARCHAR(20)              NOT NULL,
    province             VARCHAR(30)              NOT NULL,
    city                 VARCHAR(60)              NOT NULL,
    work_mode            VARCHAR(10)              NOT NULL,
    contract_type        VARCHAR(20)              NOT NULL,
    experience_level     VARCHAR(10)              NOT NULL,
    min_experience_years INTEGER                  NOT NULL,
    vacancies            INTEGER                  NOT NULL,
    -- Sanitized HTML (p, strong, em, ul, li, br only) and its plain text (search)
    description          TEXT                     NOT NULL,
    description_text     TEXT                     NOT NULL,
    responsibilities     TEXT[]                   NOT NULL DEFAULT '{}',
    benefits             TEXT[]                   NOT NULL DEFAULT '{}',
    salary_min           NUMERIC(19, 2),
    salary_max           NUMERIC(19, 2),
    salary_currency      VARCHAR(3),
    salary_period        VARCHAR(5),
    salary_visible       BOOLEAN,
    status               VARCHAR(10)              NOT NULL,
    is_featured          BOOLEAN                  NOT NULL DEFAULT FALSE,
    -- Position for expatriates (angolanization report)
    is_expatriate        BOOLEAN                  NOT NULL DEFAULT FALSE,
    recruiter_id         UUID                     NOT NULL,
    -- Last day to apply (inclusive, Angola time)
    deadline             DATE,
    published_at         TIMESTAMP WITH TIME ZONE,
    closed_at            TIMESTAMP WITH TIME ZONE,
    -- Client job request this job was created from (at most one job per request)
    job_request_id       UUID,
    -- Optimistic locking (concurrent edits)
    version              BIGINT                   NOT NULL DEFAULT 0,
    search_vector        TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('pt_unaccent'::regconfig, title), 'A')
        || setweight(to_tsvector('pt_unaccent'::regconfig, city), 'B')
        || setweight(to_tsvector('pt_unaccent'::regconfig, description_text), 'C')) STORED,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by           UUID,
    updated_by           UUID,
    CONSTRAINT uk_jobs_slug UNIQUE (slug),
    CONSTRAINT uk_jobs_job_request UNIQUE (job_request_id),
    CONSTRAINT fk_jobs_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_jobs_recruiter FOREIGN KEY (recruiter_id) REFERENCES users (id),
    CONSTRAINT fk_jobs_job_request FOREIGN KEY (job_request_id) REFERENCES job_requests (id),
    CONSTRAINT fk_jobs_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_jobs_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_jobs_status CHECK (status IN ('DRAFT', 'OPEN', 'PAUSED', 'CLOSED')),
    CONSTRAINT ck_jobs_area CHECK (area IN ('ADMINISTRATION', 'FINANCE', 'LOGISTICS', 'TRANSPORT', 'HSE',
                                            'OIL_GAS', 'ENGINEERING', 'MAINTENANCE', 'CONSTRUCTION', 'IT')),
    CONSTRAINT ck_jobs_work_mode CHECK (work_mode IN ('ONSITE', 'REMOTE', 'HYBRID')),
    CONSTRAINT ck_jobs_contract_type CHECK (contract_type IN ('PERMANENT', 'FIXED_TERM', 'TEMPORARY', 'INTERNSHIP', 'SERVICE')),
    CONSTRAINT ck_jobs_experience_level CHECK (experience_level IN ('ENTRY', 'JUNIOR', 'MID', 'SENIOR', 'LEAD')),
    CONSTRAINT ck_jobs_min_experience CHECK (min_experience_years BETWEEN 0 AND 40),
    CONSTRAINT ck_jobs_vacancies CHECK (vacancies BETWEEN 1 AND 500),
    CONSTRAINT ck_jobs_salary CHECK (
        (salary_min IS NULL AND salary_max IS NULL AND salary_currency IS NULL AND salary_period IS NULL AND salary_visible IS NULL)
        OR (salary_min > 0 AND salary_max >= salary_min AND salary_currency IN ('AOA', 'USD')
            AND salary_period IN ('MONTH', 'YEAR') AND salary_visible IS NOT NULL)),
    CONSTRAINT ck_jobs_published_at CHECK (status NOT IN ('OPEN', 'PAUSED') OR published_at IS NOT NULL),
    CONSTRAINT ck_jobs_closed_at CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);

CREATE INDEX ix_jobs_search ON jobs USING GIN (search_vector);
-- Public portal: open jobs, newest first
CREATE INDEX ix_jobs_public ON jobs (published_at DESC) WHERE status = 'OPEN';
CREATE INDEX ix_jobs_status ON jobs (status);
CREATE INDEX ix_jobs_area ON jobs (area);
CREATE INDEX ix_jobs_province ON jobs (province);
CREATE INDEX ix_jobs_client_id ON jobs (client_id);
CREATE INDEX ix_jobs_recruiter_id ON jobs (recruiter_id);
