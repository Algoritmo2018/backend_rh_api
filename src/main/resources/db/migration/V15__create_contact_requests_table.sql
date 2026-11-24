-- Public forms "Precisa de recrutar?" (company) and Contacto (general).
-- Personal data kept only to answer the request (Lei n.º 22/11); deleted 12 months after being closed
-- (scheduled job, stage 15). The sender's IP address is never stored.
CREATE TABLE contact_requests (
    id         UUID PRIMARY KEY,
    kind       VARCHAR(10)              NOT NULL,
    name       VARCHAR(120)             NOT NULL,
    company    VARCHAR(120),
    email      VARCHAR(160)             NOT NULL,
    phone      VARCHAR(16)              NOT NULL,
    message    VARCHAR(2000)            NOT NULL,
    vacancies  INTEGER,
    status     VARCHAR(10)              NOT NULL DEFAULT 'NEW',
    closed_at  TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_contact_requests_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_contact_requests_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_contact_requests_kind CHECK (kind IN ('COMPANY', 'GENERAL')),
    CONSTRAINT ck_contact_requests_status CHECK (status IN ('NEW', 'CONTACTED', 'CLOSED')),
    CONSTRAINT ck_contact_requests_company CHECK (kind <> 'COMPANY' OR (company IS NOT NULL AND vacancies BETWEEN 1 AND 500)),
    CONSTRAINT ck_contact_requests_closed_at CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);

CREATE INDEX ix_contact_requests_created_at ON contact_requests (created_at DESC);
CREATE INDEX ix_contact_requests_closed_at ON contact_requests (closed_at) WHERE closed_at IS NOT NULL;
