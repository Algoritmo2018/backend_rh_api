-- Stage 8c: sharing applications with the client company.

-- Whether a job-application consent text covers sharing with the client company (Lei n.º 22/11). The seeded text
-- 2026.1 says "incluindo a partilha com a empresa cliente"; versions published before this flag existed were never
-- confirmed by an administrator, so they stay false (their applications cannot be shared).
ALTER TABLE consent_text_versions ADD COLUMN includes_client_sharing BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE consent_text_versions SET includes_client_sharing = TRUE WHERE version = '2026.1';

-- "CV para o cliente": a PDF uploaded by the recruiter, the only file the client may download
ALTER TABLE documents DROP CONSTRAINT ck_documents_type;
ALTER TABLE documents ADD CONSTRAINT ck_documents_type
    CHECK (type IN ('CV', 'BI', 'NIF', 'INSS', 'CRIMINAL_RECORD', 'MEDICAL_CERTIFICATE', 'CERTIFICATE', 'PHOTO', 'OTHER', 'CLIENT_CV'));

ALTER TABLE applications ADD COLUMN client_cv_document_id UUID;
ALTER TABLE applications ADD CONSTRAINT fk_applications_client_cv FOREIGN KEY (client_cv_document_id) REFERENCES documents (id) ON DELETE SET NULL;

-- The application was made with the candidate's stored profile (logged-in candidate or added from the talent pool):
-- only then does the client see the stored profile; an anonymous application shows only what it sent.
ALTER TABLE applications ADD COLUMN from_profile BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE applications SET from_profile = TRUE WHERE created_by IS NOT NULL;

-- Shortlist comments (FEH OIRAM now; the client in stage 13). Deleted with the candidate's personal data (D8-8).
CREATE TABLE client_comments (
    id             UUID PRIMARY KEY,
    application_id UUID                     NOT NULL,
    author_id      UUID,
    from_client    BOOLEAN                  NOT NULL,
    text           VARCHAR(1000)            NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_client_comments_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_client_comments_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX ix_client_comments_application ON client_comments (application_id, created_at);

-- Sharing several applications at once is a bulk action too
ALTER TABLE application_bulk_actions DROP CONSTRAINT ck_application_bulk_actions_action;
ALTER TABLE application_bulk_actions ADD CONSTRAINT ck_application_bulk_actions_action CHECK (action IN ('MOVE', 'REJECT', 'ADD_TO_JOB', 'SHARE'));
