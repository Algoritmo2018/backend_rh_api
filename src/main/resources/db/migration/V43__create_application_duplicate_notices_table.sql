-- At most one "you already applied to this job" email per (email, job) per day (stage 8a adjustment). Only a
-- SHA-256 of "email|jobId" is kept, never the email itself; rows older than a day are deleted daily.
CREATE TABLE application_duplicate_notices (
    key_hash VARCHAR(64)              PRIMARY KEY,
    sent_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
