-- Staff who interview candidates of a job
CREATE TABLE job_interviewers (
    job_id  UUID NOT NULL,
    user_id UUID NOT NULL,
    CONSTRAINT pk_job_interviewers PRIMARY KEY (job_id, user_id),
    CONSTRAINT fk_job_interviewers_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_interviewers_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_job_interviewers_user_id ON job_interviewers (user_id);
