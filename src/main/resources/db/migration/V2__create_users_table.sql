-- Accounts of every role (candidates, internal staff and client-portal users)
CREATE TABLE users (
    id                    UUID PRIMARY KEY,
    name                  VARCHAR(120)             NOT NULL,
    email                 VARCHAR(160)             NOT NULL,
    phone                 VARCHAR(16)              NOT NULL,
    role                  VARCHAR(20)              NOT NULL,
    avatar_url            VARCHAR(500),
    job_title             VARCHAR(80),
    -- Foreign keys added by the clients (stage 4) and candidates (stage 6) migrations
    client_id             UUID,
    candidate_id          UUID,
    -- NULL until the invitation is accepted
    password_hash         VARCHAR(100),
    email_verified_at     TIMESTAMP WITH TIME ZONE,
    terms_accepted_at     TIMESTAMP WITH TIME ZONE,
    two_factor_enabled    BOOLEAN                  NOT NULL DEFAULT FALSE,
    active                BOOLEAN                  NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER                  NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP WITH TIME ZONE,
    last_login_at         TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone UNIQUE (phone),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('CANDIDATE', 'ADMIN', 'RECRUITER', 'INTERVIEWER', 'CLIENT')),
    CONSTRAINT ck_users_client_role CHECK (role <> 'CLIENT' OR client_id IS NOT NULL),
    CONSTRAINT ck_users_failed_login_attempts CHECK (failed_login_attempts >= 0)
);

CREATE INDEX ix_users_role ON users (role);
CREATE INDEX ix_users_client_id ON users (client_id);
