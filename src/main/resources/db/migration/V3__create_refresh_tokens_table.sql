-- Refresh tokens (only the SHA-256 hash is stored). All tokens of one login share a family_id;
-- each refresh rotates the token inside the family.
CREATE TABLE refresh_tokens (
    id                UUID PRIMARY KEY,
    user_id           UUID                     NOT NULL,
    family_id         UUID                     NOT NULL,
    -- Start of the login session: the family expires after app.jwt.refresh-family-max-age
    family_created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    token_hash        VARCHAR(64)              NOT NULL,
    expires_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Set when this token was exchanged for a new one (reuse detection)
    rotated_at        TIMESTAMP WITH TIME ZONE,
    revoked_at        TIMESTAMP WITH TIME ZONE,
    replaced_by_id    UUID,
    user_agent        VARCHAR(255),
    ip_address        VARCHAR(45),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family_id ON refresh_tokens (family_id);
