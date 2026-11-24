-- Professional references of a candidate, checked by the recruiters
CREATE TABLE candidate_references (
    id             UUID PRIMARY KEY,
    candidate_id   UUID                     NOT NULL,
    -- Application the reference was collected for (foreign key in stage 8)
    application_id UUID,
    name           VARCHAR(120)             NOT NULL,
    company        VARCHAR(80)              NOT NULL,
    role           VARCHAR(80)              NOT NULL,
    relationship   VARCHAR(60)              NOT NULL,
    phone          VARCHAR(16)              NOT NULL,
    email          VARCHAR(160),
    status         VARCHAR(12)              NOT NULL,
    notes          VARCHAR(1000),
    checked_by_id  UUID,
    checked_at     TIMESTAMP WITH TIME ZONE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by     UUID,
    updated_by     UUID,
    CONSTRAINT fk_candidate_references_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id) ON DELETE CASCADE,
    CONSTRAINT fk_candidate_references_checked_by FOREIGN KEY (checked_by_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_candidate_references_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_candidate_references_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_candidate_references_status CHECK (status IN ('PENDING', 'CONTACTED', 'VERIFIED', 'UNREACHABLE', 'NEGATIVE'))
);

CREATE INDEX ix_candidate_references_candidate_id ON candidate_references (candidate_id, created_at DESC);
