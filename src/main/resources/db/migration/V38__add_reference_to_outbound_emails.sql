-- Record that an outbox email belongs to (e.g. a message to a candidate), so its delivery status can be followed
-- and the email deleted with that record. Not a foreign key: the outbox does not know the other modules.
ALTER TABLE outbound_emails ADD COLUMN reference_id UUID;

CREATE INDEX ix_outbound_emails_reference ON outbound_emails (reference_id) WHERE reference_id IS NOT NULL;
