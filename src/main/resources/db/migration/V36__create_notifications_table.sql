-- In-app notifications (the bell). Title and body are stored in Portuguese when created; the link is a path of
-- the frontend. Read ones are deleted after 90 days, every one after 180 days (daily job).
CREATE TABLE notifications (
    id         UUID PRIMARY KEY,
    user_id    UUID                     NOT NULL,
    type       VARCHAR(40)              NOT NULL,
    title      VARCHAR(200)             NOT NULL,
    body       VARCHAR(1000)            NOT NULL,
    link       VARCHAR(500),
    read_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);
-- Unread counter of the bell (polled every minute)
CREATE INDEX ix_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;
