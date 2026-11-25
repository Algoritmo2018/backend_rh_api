-- Applications of candidates to jobs (stage 8). One per (job, candidate). The application keeps its own copy of
-- what was sent (name, email, phone, province): later changes to the profile or to the account email never change
-- it (D8-1). The score and the requirement matches are computed once, when applying (D8-3).
CREATE TABLE applications (
    id                     UUID PRIMARY KEY,
    job_id                 UUID                     NOT NULL,
    candidate_id           UUID                     NOT NULL,
    stage                  VARCHAR(20)              NOT NULL,
    status                 VARCHAR(10)              NOT NULL,
    -- Copy of the data sent with the application (blanked on erasure)
    full_name              VARCHAR(120)             NOT NULL,
    email                  VARCHAR(160)             NOT NULL,
    phone                  VARCHAR(16)              NOT NULL,
    province               VARCHAR(30),
    cv_document_id         UUID,
    -- Quick profile, sent instead of a CV
    quick_headline         VARCHAR(80),
    quick_skills           TEXT[],
    quick_years            INTEGER,
    quick_education_level  VARCHAR(20),
    quick_last_role        VARCHAR(80),
    quick_last_company     VARCHAR(80),
    quick_driver_licenses  TEXT[],
    score                  INTEGER                  NOT NULL,
    eliminated             BOOLEAN                  NOT NULL,
    rating                 INTEGER                  NOT NULL DEFAULT 0,
    source                 VARCHAR(20)              NOT NULL,
    rejection_reason_id    UUID,
    rejection_note         VARCHAR(500),
    rejected_at            TIMESTAMP WITH TIME ZONE,
    rejected_by            UUID,
    shared_with_client     BOOLEAN                  NOT NULL DEFAULT FALSE,
    shared_at              TIMESTAMP WITH TIME ZONE,
    client_decision        VARCHAR(10),
    recruiter_summary      TEXT,
    applied_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    stage_entered_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    version                BIGINT                   NOT NULL DEFAULT 0,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by             UUID,
    updated_by             UUID,
    CONSTRAINT uk_applications_job_candidate UNIQUE (job_id, candidate_id),
    CONSTRAINT fk_applications_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_applications_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id),
    CONSTRAINT fk_applications_cv FOREIGN KEY (cv_document_id) REFERENCES documents (id) ON DELETE SET NULL,
    CONSTRAINT fk_applications_rejection_reason FOREIGN KEY (rejection_reason_id) REFERENCES rejection_reasons (id),
    CONSTRAINT fk_applications_rejected_by FOREIGN KEY (rejected_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_applications_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_applications_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_applications_stage CHECK (stage IN ('NEW', 'SCREENING', 'HR_INTERVIEW', 'TECHNICAL_TEST', 'CLIENT_INTERVIEW', 'OFFER', 'HIRED')),
    CONSTRAINT ck_applications_status CHECK (status IN ('ACTIVE', 'REJECTED', 'WITHDRAWN', 'HIRED')),
    CONSTRAINT ck_applications_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_applications_rating CHECK (rating BETWEEN 0 AND 5),
    CONSTRAINT ck_applications_quick_years CHECK (quick_years IS NULL OR quick_years BETWEEN 0 AND 50),
    CONSTRAINT ck_applications_client_decision CHECK (client_decision IS NULL OR client_decision IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX ix_applications_candidate ON applications (candidate_id);
CREATE INDEX ix_applications_job_stage ON applications (job_id, stage, status);

-- Answers to the screening questions. The question text is copied (it may be corrected later); no foreign key to
-- the question, which cannot be deleted once the job has applications (stage 8 rule).
CREATE TABLE application_answers (
    application_id UUID         NOT NULL,
    position       INTEGER      NOT NULL,
    question_id    UUID         NOT NULL,
    question       VARCHAR(200) NOT NULL,
    answer         TEXT         NOT NULL,
    CONSTRAINT pk_application_answers PRIMARY KEY (application_id, position),
    CONSTRAINT fk_application_answers_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE
);

-- Match of each job requirement at application time (frontend RequirementMatch)
CREATE TABLE application_requirement_matches (
    application_id UUID         NOT NULL,
    position       INTEGER      NOT NULL,
    requirement_id UUID         NOT NULL,
    met            BOOLEAN      NOT NULL,
    detail         VARCHAR(300) NOT NULL,
    CONSTRAINT pk_application_requirement_matches PRIMARY KEY (application_id, position),
    CONSTRAINT fk_application_matches_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE
);

-- Tags of an application (tags are hard-deleted: the link goes with them, D3-2)
CREATE TABLE application_tags (
    application_id UUID NOT NULL,
    tag_id         UUID NOT NULL,
    CONSTRAINT pk_application_tags PRIMARY KEY (application_id, tag_id),
    CONSTRAINT fk_application_tags_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_application_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
);

-- Audit trail of the pipeline (frontend StageMove). by_user_id NULL = the system or the candidate.
CREATE TABLE stage_moves (
    id             UUID PRIMARY KEY,
    application_id UUID                     NOT NULL,
    action         VARCHAR(10)              NOT NULL,
    from_stage     VARCHAR(20),
    to_stage       VARCHAR(20),
    by_user_id     UUID,
    reason         VARCHAR(500),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_stage_moves_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_stage_moves_user FOREIGN KEY (by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_stage_moves_action CHECK (action IN ('APPLIED', 'MOVED', 'REJECTED', 'RESTORED', 'WITHDRAWN'))
);

CREATE INDEX ix_stage_moves_application ON stage_moves (application_id, created_at DESC);
