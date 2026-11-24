-- Consents of a candidate (Lei n.º 22/11). History, never overwritten: granting adds a row with the
-- version of the text accepted; revoking stamps revoked_at on the active row.
CREATE TABLE consents (
    id           UUID PRIMARY KEY,
    candidate_id UUID                     NOT NULL,
    type         VARCHAR(20)              NOT NULL,
    -- Job of a "job_application" consent (stage 8)
    job_id       UUID,
    granted      BOOLEAN                  NOT NULL,
    text_version VARCHAR(20)              NOT NULL,
    granted_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at   TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_consents_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id) ON DELETE CASCADE,
    CONSTRAINT fk_consents_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_consents_text_version FOREIGN KEY (text_version) REFERENCES consent_text_versions (version),
    CONSTRAINT ck_consents_type CHECK (type IN ('JOB_APPLICATION', 'TALENT_POOL', 'MARKETING')),
    CONSTRAINT ck_consents_job CHECK ((type = 'JOB_APPLICATION') = (job_id IS NOT NULL)),
    CONSTRAINT ck_consents_revoked CHECK (revoked_at IS NULL OR revoked_at >= granted_at)
);

CREATE INDEX ix_consents_candidate_id ON consents (candidate_id, type);
-- At most one active general consent (talent pool, marketing) of each type per candidate
CREATE UNIQUE INDEX uk_consents_active ON consents (candidate_id, type) WHERE revoked_at IS NULL AND job_id IS NULL;
