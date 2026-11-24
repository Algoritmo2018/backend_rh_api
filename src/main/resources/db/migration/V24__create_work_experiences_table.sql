-- Work experience of a candidate. Dates are months ("YYYY-MM" in JSON), stored as the first day.
CREATE TABLE work_experiences (
    id           UUID PRIMARY KEY,
    candidate_id UUID                     NOT NULL,
    position     INTEGER                  NOT NULL,
    company      VARCHAR(80)              NOT NULL,
    role         VARCHAR(80)              NOT NULL,
    province     VARCHAR(30),
    start_date   DATE                     NOT NULL,
    end_date     DATE,
    is_current   BOOLEAN                  NOT NULL,
    description  VARCHAR(500),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT fk_work_experiences_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id) ON DELETE CASCADE,
    CONSTRAINT fk_work_experiences_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_work_experiences_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_work_experiences_dates CHECK (
        (is_current AND end_date IS NULL) OR (NOT is_current AND end_date IS NOT NULL AND end_date >= start_date))
);

CREATE INDEX ix_work_experiences_candidate_id ON work_experiences (candidate_id, position);
