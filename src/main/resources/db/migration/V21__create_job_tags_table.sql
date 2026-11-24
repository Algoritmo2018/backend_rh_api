-- Tags of a job. Deleting a tag removes it from every job (decision D3-2)
CREATE TABLE job_tags (
    job_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    CONSTRAINT pk_job_tags PRIMARY KEY (job_id, tag_id),
    CONSTRAINT fk_job_tags_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
);

CREATE INDEX ix_job_tags_tag_id ON job_tags (tag_id);
