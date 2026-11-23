-- Single-use tokens: links sent by email (hash = SHA-256 of the token) and SMS codes
-- (hash = HMAC-SHA256 with a server-side key, because a 6-digit code is easy to brute-force).
CREATE TABLE one_time_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID                     NOT NULL,
    type       VARCHAR(30)              NOT NULL,
    token_hash VARCHAR(64)              NOT NULL,
    -- Two-factor login: the challenge token identifies the attempt, this is the SMS code
    code_hash  VARCHAR(64),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    attempts   INTEGER                  NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_one_time_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_one_time_tokens_type CHECK (type IN (
        'PASSWORD_RESET', 'INVITATION', 'EMAIL_VERIFICATION', 'LOGIN_OTP', 'TWO_FACTOR_SETUP', 'TWO_FACTOR_LOGIN')),
    CONSTRAINT ck_one_time_tokens_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_one_time_tokens_type_hash ON one_time_tokens (type, token_hash);
CREATE INDEX ix_one_time_tokens_user_type ON one_time_tokens (user_id, type);
