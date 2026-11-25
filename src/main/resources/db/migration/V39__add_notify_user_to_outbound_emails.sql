-- Who is told in the bell when an outbox email fails for good: the user who sent it (e.g. a recruiter's message to a
-- candidate). NULL (system emails, automatic messages, or a deleted user) = every active administrator.
ALTER TABLE outbound_emails ADD COLUMN notify_user_id UUID;
ALTER TABLE outbound_emails
    ADD CONSTRAINT fk_outbound_emails_notify_user FOREIGN KEY (notify_user_id) REFERENCES users (id) ON DELETE SET NULL;
