-- Record of every anonymization of a candidate (Lei n.º 22/11: prove the erasure happened) WITHOUT any
-- personal data: not even the candidate id is kept, only when, why, who executed it and how many files
-- were deleted.
CREATE TABLE erasure_log (
    id                UUID PRIMARY KEY,
    erased_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    reason            VARCHAR(20)              NOT NULL,
    -- User who executed it; NULL for the scheduled retention job
    executed_by       UUID,
    documents_deleted INTEGER                  NOT NULL DEFAULT 0,
    CONSTRAINT fk_erasure_log_executed_by FOREIGN KEY (executed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_erasure_log_reason CHECK (reason IN ('HOLDER_REQUEST', 'RETENTION')),
    CONSTRAINT ck_erasure_log_documents CHECK (documents_deleted >= 0)
);

CREATE INDEX ix_erasure_log_erased_at ON erasure_log (erased_at DESC);
