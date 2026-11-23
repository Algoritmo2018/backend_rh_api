-- Pipeline stages: the 7 keys are fixed; name, position and automatic message are configurable.
-- The position constraint is deferred so stages can swap positions within one transaction.
CREATE TABLE pipeline_stages (
    id                      UUID PRIMARY KEY,
    stage_key               VARCHAR(30)              NOT NULL,
    name                    VARCHAR(40)              NOT NULL,
    position                INTEGER                  NOT NULL,
    color                   VARCHAR(20)              NOT NULL,
    auto_action_template_id UUID,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by              UUID,
    updated_by              UUID,
    CONSTRAINT uk_pipeline_stages_key UNIQUE (stage_key),
    CONSTRAINT uk_pipeline_stages_position UNIQUE (position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_pipeline_stages_template FOREIGN KEY (auto_action_template_id) REFERENCES message_templates (id),
    CONSTRAINT fk_pipeline_stages_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_pipeline_stages_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_pipeline_stages_key CHECK (stage_key IN (
        'NEW', 'SCREENING', 'HR_INTERVIEW', 'TECHNICAL_TEST', 'CLIENT_INTERVIEW', 'OFFER', 'HIRED')),
    CONSTRAINT ck_pipeline_stages_position CHECK (position BETWEEN 1 AND 7),
    CONSTRAINT ck_pipeline_stages_fixed_ends CHECK (
        (stage_key <> 'NEW' OR position = 1) AND (stage_key <> 'HIRED' OR position = 7)),
    CONSTRAINT ck_pipeline_stages_color CHECK (color IN ('SLATE', 'CYAN', 'BLUE', 'INDIGO', 'PURPLE', 'WARNING', 'SUCCESS'))
);

-- Seed: names and automatic actions of the frontend (mocks/catalogs.ts)
INSERT INTO pipeline_stages (id, stage_key, name, position, color, auto_action_template_id, created_at, updated_at)
SELECT gen_random_uuid(), s.stage_key, s.name, s.position, s.color, t.id, now(), now()
FROM (VALUES
    ('NEW', 'Nova', 1, 'SLATE', 'app-received-email'),
    ('SCREENING', 'Triagem', 2, 'CYAN', NULL),
    ('HR_INTERVIEW', 'Entrevista RH', 3, 'BLUE', 'interview-invite-wa'),
    ('TECHNICAL_TEST', 'Teste Técnico', 4, 'INDIGO', 'test-invite-email'),
    ('CLIENT_INTERVIEW', 'Entrevista Cliente', 5, 'PURPLE', 'interview-invite-wa'),
    ('OFFER', 'Proposta', 6, 'WARNING', 'offer-email'),
    ('HIRED', 'Contratado', 7, 'SUCCESS', 'documents-wa')
) AS s (stage_key, name, position, color, template_code)
LEFT JOIN message_templates t ON t.code = s.template_code;
