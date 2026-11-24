-- Candidate profiles. A candidate may exist without an account (applied without registering, came
-- from WhatsApp…); an account is linked by email only after the email is confirmed.
CREATE TABLE candidates (
    id                          UUID PRIMARY KEY,
    user_id                     UUID,
    full_name                   VARCHAR(120)             NOT NULL,
    -- Identity of a candidate without account (applications are linked by it); lower case
    email                       VARCHAR(160)             NOT NULL,
    phone                       VARCHAR(16)              NOT NULL,
    whatsapp                    VARCHAR(16),
    -- Never used in the match score nor in the talent search
    gender                      VARCHAR(1),
    birth_date                  DATE,
    nationality                 VARCHAR(40)              NOT NULL,
    -- false = expatriate (angolanization report)
    is_angolan                  BOOLEAN                  NOT NULL DEFAULT TRUE,
    province                    VARCHAR(30),
    city                        VARCHAR(60)              NOT NULL DEFAULT '',
    headline                    VARCHAR(80)              NOT NULL DEFAULT '',
    summary                     VARCHAR(600),
    skills                      TEXT[]                   NOT NULL DEFAULT '{}',
    driver_licenses             TEXT[]                   NOT NULL DEFAULT '{}',
    availability                VARCHAR(10)              NOT NULL,
    salary_expectation_amount   NUMERIC(19, 2),
    salary_expectation_currency VARCHAR(3),
    -- Current CV document (foreign key added with the documents table, stage 6b)
    cv_document_id              UUID,
    -- Recalculated from the work experience on every save
    total_experience_years      INTEGER                  NOT NULL DEFAULT 0,
    source                      VARCHAR(20)              NOT NULL,
    -- Mirror of an active "talent_pool" consent (kept by the consents service)
    in_talent_pool              BOOLEAN                  NOT NULL DEFAULT FALSE,
    -- Profile text for the talent search (headline, summary, skills, roles, companies, degrees…)
    search_text                 TEXT                     NOT NULL DEFAULT '',
    search_vector               TSVECTOR GENERATED ALWAYS AS (to_tsvector('pt_unaccent'::regconfig, search_text)) STORED,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by                  UUID,
    updated_by                  UUID,
    CONSTRAINT uk_candidates_email UNIQUE (email),
    CONSTRAINT uk_candidates_user UNIQUE (user_id),
    CONSTRAINT ck_candidates_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT fk_candidates_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_candidates_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_candidates_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_candidates_gender CHECK (gender IN ('F', 'M')),
    CONSTRAINT ck_candidates_availability CHECK (availability IN ('IMMEDIATE', 'DAYS_15', 'DAYS_30', 'DAYS_60', 'NEGOTIABLE')),
    CONSTRAINT ck_candidates_driver_licenses CHECK (driver_licenses <@ ARRAY['A', 'B', 'C', 'D', 'E']::TEXT[]),
    CONSTRAINT ck_candidates_salary CHECK (
        (salary_expectation_amount IS NULL AND salary_expectation_currency IS NULL)
        OR (salary_expectation_amount >= 0 AND salary_expectation_currency IN ('AOA', 'USD'))),
    CONSTRAINT ck_candidates_experience CHECK (total_experience_years >= 0)
);

CREATE INDEX ix_candidates_search ON candidates USING GIN (search_vector);
-- Talent pool: only candidates with an active consent
CREATE INDEX ix_candidates_talent_pool ON candidates (total_experience_years DESC) WHERE in_talent_pool;
CREATE INDEX ix_candidates_province ON candidates (province);

ALTER TABLE users
    ADD CONSTRAINT fk_users_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id) ON DELETE SET NULL,
    ADD CONSTRAINT uk_users_candidate UNIQUE (candidate_id);
