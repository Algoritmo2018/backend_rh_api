-- Offer letter templates (plain text with {{variables}}). At most one default (partial unique index).
CREATE TABLE offer_templates (
    id         UUID PRIMARY KEY,
    code       VARCHAR(60)              NOT NULL,
    name       VARCHAR(80)              NOT NULL,
    body       VARCHAR(8000)            NOT NULL,
    is_default BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uk_offer_templates_code UNIQUE (code),
    CONSTRAINT fk_offer_templates_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_offer_templates_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uk_offer_templates_single_default ON offer_templates (is_default) WHERE is_default;

-- Seed: the templates of the frontend (mocks/catalogs.ts), code = frontend id without "otpl-"
INSERT INTO offer_templates (id, code, name, body, is_default, created_at, updated_at) VALUES
(gen_random_uuid(), 'standard', 'Proposta padrão',
 E'Exmo(a). Sr(a). {{nome_completo}},\n\nNa sequência do processo de recrutamento conduzido pela FEH OIRAM – Prestação de Serviços, Lda, em representação de {{empresa}}, temos o prazer de lhe apresentar a seguinte proposta:\n\nFunção: {{cargo}}\nRemuneração base mensal: {{salario}}\nData de início: {{data_inicio}}\nBenefícios: {{beneficios}}\n\nA presente proposta é válida por 7 dias a contar da data de envio.\n\nCom os melhores cumprimentos,\n{{recrutador}}',
 TRUE, now(), now()),
(gen_random_uuid(), 'expat', 'Proposta para expatriados',
 E'Dear {{nome_completo}},\n\nOn behalf of {{empresa}}, FEH OIRAM – Prestação de Serviços, Lda is pleased to offer you the position of {{cargo}} in Angola.\n\nMonthly salary: {{salario}}\nStart date: {{data_inicio}}\nBenefits: {{beneficios}}\n\nThis offer includes work visa support and rotation travel as per company policy.\n\nKind regards,\n{{recrutador}}',
 FALSE, now(), now());
