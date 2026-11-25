-- Messages of an application (stage 8) and the automatic flag (stage 8, adjustment 1): sent_by always records the
-- user who caused a send; automatic = triggered by the system (stage automatic actions, bulk actions). Automatic
-- sends do not count in the per-hour/per-day sending limits.
ALTER TABLE messages
    ADD CONSTRAINT fk_messages_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE SET NULL;
ALTER TABLE messages ADD COLUMN automatic BOOLEAN NOT NULL DEFAULT FALSE;
-- Rows of stage 7.2 without a sender were automatic sends
UPDATE messages SET automatic = TRUE WHERE sent_by IS NULL;

CREATE INDEX ix_messages_application ON messages (application_id) WHERE application_id IS NOT NULL;
DROP INDEX ix_messages_sent_by_created;
CREATE INDEX ix_messages_sent_by_created ON messages (sent_by, created_at) WHERE sent_by IS NOT NULL AND NOT automatic;
