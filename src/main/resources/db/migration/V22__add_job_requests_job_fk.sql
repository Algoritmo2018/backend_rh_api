-- Job created from a client job request (stage 4 left the column without a foreign key)
ALTER TABLE job_requests
    ADD CONSTRAINT fk_job_requests_job FOREIGN KEY (job_id) REFERENCES jobs (id);
