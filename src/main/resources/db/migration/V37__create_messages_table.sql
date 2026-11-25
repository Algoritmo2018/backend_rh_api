-- Messages sent to candidates (email, SMS, WhatsApp). Each row is a snapshot: the final subject and body as
-- sent, plus the code of the source template. Editing or deactivating a template never changes history.
CREATE TABLE messages (
    id             UUID PRIMARY KEY,
    candidate_id   UUID                     NOT NULL,
    -- Foreign key to job_applications added in stage 8
    application_id UUID,
    channel        VARCHAR(10)              NOT NULL,
    direction      VARCHAR(10)              NOT NULL,
    subject        VARCHAR(500),
    body           TEXT                     NOT NULL,
    status         VARCHAR(10)              NOT NULL,
    template_id    UUID,
    template_code  VARCHAR(60),
    marketing      BOOLEAN                  NOT NULL DEFAULT FALSE,
    -- NULL = sent automatically by the system
    sent_by        UUID,
    -- Short reason of a failed delivery (never a password nor the content)
    failure_reason VARCHAR(500),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_messages_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_template FOREIGN KEY (template_id) REFERENCES message_templates (id) ON DELETE SET NULL,
    CONSTRAINT fk_messages_sent_by FOREIGN KEY (sent_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_messages_channel CHECK (channel IN ('EMAIL', 'SMS', 'WHATSAPP')),
    CONSTRAINT ck_messages_direction CHECK (direction IN ('OUTBOUND', 'INBOUND')),
    CONSTRAINT ck_messages_status CHECK (status IN ('QUEUED', 'SENT', 'DELIVERED', 'READ', 'FAILED')),
    CONSTRAINT ck_messages_email_subject CHECK (channel <> 'EMAIL' OR subject IS NOT NULL)
);

-- History of a candidate, the inbox (newest first) and the sending limits
CREATE INDEX ix_messages_candidate_created ON messages (candidate_id, created_at DESC);
CREATE INDEX ix_messages_created ON messages (created_at DESC);
CREATE INDEX ix_messages_sent_by_created ON messages (sent_by, created_at) WHERE sent_by IS NOT NULL;
