-- Stage 9a: interviews (staff side). Times are stored in UTC (timestamptz); texts are shown in Luanda time.

CREATE TABLE interviews (
    id                        UUID PRIMARY KEY,
    application_id            UUID                     NOT NULL,
    job_id                    UUID                     NOT NULL,
    candidate_id              UUID                     NOT NULL,
    type                      VARCHAR(10)              NOT NULL,
    mode                      VARCHAR(10)              NOT NULL,
    location                  VARCHAR(200),
    meeting_provider          VARCHAR(10),
    meeting_url               VARCHAR(500),
    -- NULL while the candidate (or the client) has not chosen one of the proposed slots
    starts_at                 TIMESTAMP WITH TIME ZONE,
    duration_minutes          INTEGER                  NOT NULL,
    status                    VARCHAR(15)              NOT NULL,
    -- Internal notes (never shown to the candidate nor the client); blanked on erasure
    notes                     VARCHAR(1000),
    -- SEQUENCE of the calendar invitation (.ics): incremented on every change, same UID
    ics_sequence              INTEGER                  NOT NULL DEFAULT 0,
    reminder_sent_at          TIMESTAMP WITH TIME ZONE,
    slots_expired_notified_at TIMESTAMP WITH TIME ZONE,
    version                   BIGINT                   NOT NULL DEFAULT 0,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by                UUID,
    updated_by                UUID,
    CONSTRAINT fk_interviews_application FOREIGN KEY (application_id) REFERENCES applications (id),
    CONSTRAINT fk_interviews_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_interviews_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id),
    CONSTRAINT fk_interviews_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_interviews_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_interviews_type CHECK (type IN ('HR', 'TECHNICAL', 'CLIENT')),
    CONSTRAINT ck_interviews_mode CHECK (mode IN ('ONSITE', 'VIDEO', 'PHONE')),
    CONSTRAINT ck_interviews_provider CHECK (meeting_provider IS NULL OR meeting_provider IN ('MEET', 'TEAMS')),
    CONSTRAINT ck_interviews_status CHECK (status IN ('PENDING_SLOT', 'SCHEDULED', 'DONE', 'NO_SHOW', 'CANCELLED')),
    CONSTRAINT ck_interviews_duration CHECK (duration_minutes BETWEEN 15 AND 240),
    CONSTRAINT ck_interviews_time CHECK (status IN ('PENDING_SLOT', 'CANCELLED') OR starts_at IS NOT NULL)
);

CREATE INDEX ix_interviews_starts_at ON interviews (starts_at);
CREATE INDEX ix_interviews_application ON interviews (application_id);
CREATE INDEX ix_interviews_candidate ON interviews (candidate_id);
CREATE INDEX ix_interviews_job ON interviews (job_id);

-- Times proposed for the candidate (or the client, for client interviews) to choose
CREATE TABLE interview_proposed_slots (
    interview_id UUID                     NOT NULL,
    position     INTEGER                  NOT NULL,
    starts_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_interview_proposed_slots PRIMARY KEY (interview_id, position),
    CONSTRAINT fk_interview_slots_interview FOREIGN KEY (interview_id) REFERENCES interviews (id) ON DELETE CASCADE
);

CREATE TABLE interview_interviewers (
    interview_id UUID NOT NULL,
    user_id      UUID NOT NULL,
    CONSTRAINT pk_interview_interviewers PRIMARY KEY (interview_id, user_id),
    CONSTRAINT fk_interview_interviewers_interview FOREIGN KEY (interview_id) REFERENCES interviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_interview_interviewers_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_interview_interviewers_user ON interview_interviewers (user_id);

-- Portal users of the client company who take part in a client interview
CREATE TABLE interview_client_participants (
    interview_id UUID NOT NULL,
    user_id      UUID NOT NULL,
    CONSTRAINT pk_interview_client_participants PRIMARY KEY (interview_id, user_id),
    CONSTRAINT fk_interview_participants_interview FOREIGN KEY (interview_id) REFERENCES interviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_interview_participants_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Text attachment of an outbox email (the .ics calendar invitation)
ALTER TABLE outbound_emails ADD COLUMN attachment_name VARCHAR(100);
ALTER TABLE outbound_emails ADD COLUMN attachment_content_type VARCHAR(100);
ALTER TABLE outbound_emails ADD COLUMN attachment_content TEXT;

-- D9-3: the invitation templates are used by the code (cannot be deactivated) and two new system templates
UPDATE message_templates SET is_system = TRUE, active = TRUE WHERE code IN ('interview-invite-email', 'interview-invite-wa');

INSERT INTO message_templates (id, code, name, channel, category, subject, body, active, is_system, created_at, updated_at) VALUES
(gen_random_uuid(), 'interview-slots-email', 'Escolha do horário da entrevista', 'EMAIL', 'INTERVIEW_INVITE',
 'Escolha o horário da sua entrevista – {{vaga}}',
 E'Olá {{nome}},\n\nFoi convidado(a) para uma entrevista para a vaga de {{vaga}}.\n\nPropusemos alguns horários: escolha o que lhe for mais conveniente na sua área de candidato, em {{link}}.\n\nCumprimentos,\n{{recrutador}}\nFEH OIRAM',
 TRUE, TRUE, now(), now()),
(gen_random_uuid(), 'interview-cancelled-email', 'Entrevista cancelada', 'EMAIL', 'GENERAL',
 'Entrevista cancelada – {{vaga}}',
 E'Olá {{nome}},\n\nInformamos que a entrevista para a vaga de {{vaga}} ({{data_entrevista}}) foi cancelada.\n\nSe for necessário, entraremos em contacto consigo.\n\nCumprimentos,\nEquipa de Recrutamento FEH OIRAM',
 TRUE, TRUE, now(), now());

-- D9-1: a stage automatic action cannot need the interview (or offer) data, unknown when the stage changes.
-- The seeded actions of "Entrevista RH", "Entrevista Cliente" and "Proposta" are removed (only if unchanged).
UPDATE pipeline_stages SET auto_action_template_id = NULL
WHERE stage_key IN ('HR_INTERVIEW', 'CLIENT_INTERVIEW', 'OFFER')
  AND auto_action_template_id IN (SELECT id FROM message_templates WHERE code IN ('interview-invite-wa', 'offer-email'));
