-- Who opened or downloaded each document and when (Lei n.º 22/11: accountability for personal data).
-- No IP address (minimization, decision D6-13). Kept when the document is deleted (document_id → NULL).
CREATE TABLE document_access_log (
    id            UUID PRIMARY KEY,
    document_id   UUID,
    candidate_id  UUID                     NOT NULL,
    document_type VARCHAR(20)              NOT NULL,
    user_id       UUID,
    action        VARCHAR(10)              NOT NULL,
    accessed_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_document_access_log_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE SET NULL,
    CONSTRAINT fk_document_access_log_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id) ON DELETE CASCADE,
    CONSTRAINT fk_document_access_log_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_document_access_log_action CHECK (action IN ('VIEW', 'DOWNLOAD'))
);

CREATE INDEX ix_document_access_log_candidate_id ON document_access_log (candidate_id, accessed_at DESC);
CREATE INDEX ix_document_access_log_document_id ON document_access_log (document_id);
