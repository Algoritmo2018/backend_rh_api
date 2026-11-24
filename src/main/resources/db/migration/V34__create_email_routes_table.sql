-- Which account sends each kind of email. A purpose without a route uses the default server (spring.mail).
CREATE TABLE email_routes (
    id         UUID PRIMARY KEY,
    purpose    VARCHAR(15)              NOT NULL,
    account_id UUID                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uk_email_routes_purpose UNIQUE (purpose),
    CONSTRAINT fk_email_routes_account FOREIGN KEY (account_id) REFERENCES email_accounts (id),
    CONSTRAINT fk_email_routes_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_email_routes_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_email_routes_purpose CHECK (purpose IN ('SYSTEM', 'RECRUITMENT', 'CLIENTS'))
);
