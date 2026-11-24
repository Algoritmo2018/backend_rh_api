-- Job requests made by client companies in the client portal, reviewed by FEH OIRAM
CREATE TABLE job_requests (
    id                     UUID PRIMARY KEY,
    client_id              UUID                     NOT NULL,
    requested_by_id        UUID,
    title                  VARCHAR(120)             NOT NULL,
    area                   VARCHAR(20)              NOT NULL,
    province               VARCHAR(30)              NOT NULL,
    vacancies              INTEGER                  NOT NULL,
    description            VARCHAR(3000)            NOT NULL,
    requirements           VARCHAR(3000)            NOT NULL,
    salary_budget_amount   NUMERIC(19, 2),
    salary_budget_currency VARCHAR(3),
    desired_start_date     DATE,
    urgency                VARCHAR(10)              NOT NULL,
    status                 VARCHAR(10)              NOT NULL DEFAULT 'PENDING',
    -- Job created from the request (foreign key added with the jobs table, stage 5)
    job_id                 UUID,
    review_note            VARCHAR(1000),
    reviewed_at            TIMESTAMP WITH TIME ZONE,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by             UUID,
    updated_by             UUID,
    CONSTRAINT fk_job_requests_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_job_requests_requested_by FOREIGN KEY (requested_by_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_job_requests_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_job_requests_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_job_requests_area CHECK (area IN ('ADMINISTRATION', 'FINANCE', 'LOGISTICS', 'TRANSPORT', 'HSE',
                                                    'OIL_GAS', 'ENGINEERING', 'MAINTENANCE', 'CONSTRUCTION', 'IT')),
    CONSTRAINT ck_job_requests_vacancies CHECK (vacancies BETWEEN 1 AND 200),
    CONSTRAINT ck_job_requests_salary_budget CHECK (
        (salary_budget_amount IS NULL AND salary_budget_currency IS NULL)
        OR (salary_budget_amount > 0 AND salary_budget_currency IN ('AOA', 'USD'))),
    CONSTRAINT ck_job_requests_urgency CHECK (urgency IN ('LOW', 'NORMAL', 'HIGH')),
    CONSTRAINT ck_job_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CONVERTED')),
    CONSTRAINT ck_job_requests_converted_job CHECK (status <> 'CONVERTED' OR job_id IS NOT NULL)
);

CREATE INDEX ix_job_requests_client_id ON job_requests (client_id, created_at DESC);
CREATE INDEX ix_job_requests_status ON job_requests (status);
