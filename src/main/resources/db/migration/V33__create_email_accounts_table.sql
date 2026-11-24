-- SMTP accounts configured by the administrators (settings tab "Emails de envio"). The password is stored
-- encrypted (AES-256-GCM, key from APP_SECRETS_KEY) and never returned by the API.
CREATE TABLE email_accounts (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(60)              NOT NULL,
    host               VARCHAR(253)             NOT NULL,
    port               INTEGER                  NOT NULL,
    security           VARCHAR(10)              NOT NULL,
    username           VARCHAR(254),
    password_encrypted TEXT,
    from_address       VARCHAR(254)             NOT NULL,
    from_name          VARCHAR(80),
    reply_to           VARCHAR(254),
    active             BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT uk_email_accounts_name UNIQUE (name),
    CONSTRAINT fk_email_accounts_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_email_accounts_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_email_accounts_port CHECK (port BETWEEN 1 AND 65535),
    CONSTRAINT ck_email_accounts_security CHECK (security IN ('NONE', 'STARTTLS', 'SSL'))
);
