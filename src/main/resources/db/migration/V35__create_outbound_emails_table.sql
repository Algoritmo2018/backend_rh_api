-- Outbox: emails written in the transaction of the event that causes them and sent by a background worker,
-- with new attempts when the SMTP server fails. Delivery is "at least once". Emails with secret links
-- (password reset, invitation, email verification) are never stored here.
CREATE TABLE outbound_emails (
    id              UUID PRIMARY KEY,
    purpose         VARCHAR(15)              NOT NULL,
    recipient       VARCHAR(254)             NOT NULL,
    subject         VARCHAR(500)             NOT NULL,
    text_body       TEXT                     NOT NULL,
    html_body       TEXT,
    status          VARCHAR(10)              NOT NULL,
    attempts        INTEGER                  NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Short error of the last attempt (never a password)
    last_error      VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at         TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_outbound_emails_purpose CHECK (purpose IN ('SYSTEM', 'RECRUITMENT', 'CLIENTS')),
    CONSTRAINT ck_outbound_emails_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_outbound_emails_attempts CHECK (attempts >= 0)
);

-- The worker only looks at pending emails that are due
CREATE INDEX ix_outbound_emails_pending ON outbound_emails (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX ix_outbound_emails_status_created ON outbound_emails (status, created_at);
