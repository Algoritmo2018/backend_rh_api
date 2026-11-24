-- Education of a candidate
CREATE TABLE educations (
    id           UUID PRIMARY KEY,
    candidate_id UUID                     NOT NULL,
    position     INTEGER                  NOT NULL,
    institution  VARCHAR(100)             NOT NULL,
    degree       VARCHAR(100)             NOT NULL,
    level        VARCHAR(10)              NOT NULL,
    field        VARCHAR(100),
    start_year   INTEGER                  NOT NULL,
    end_year     INTEGER,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT fk_educations_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id) ON DELETE CASCADE,
    CONSTRAINT fk_educations_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_educations_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_educations_level CHECK (level IN ('BASIC', 'SECONDARY', 'TECHNICAL', 'BACHELOR', 'LICENTIATE', 'MASTER', 'PHD')),
    CONSTRAINT ck_educations_years CHECK (end_year IS NULL OR end_year >= start_year)
);

CREATE INDEX ix_educations_candidate_id ON educations (candidate_id, position);
