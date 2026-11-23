-- Exact consent texts of every version (Lei n.º 22/11: prove what each person accepted). Immutable.
CREATE TABLE consent_text_versions (
    version          VARCHAR(20) PRIMARY KEY,
    job_text         VARCHAR(1500)            NOT NULL,
    talent_pool_text VARCHAR(1500)            NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by       UUID,
    CONSTRAINT fk_consent_text_versions_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
);

-- Company settings: exactly one row (id is always 1), created here and only ever updated.
CREATE TABLE company_settings (
    id                       SMALLINT PRIMARY KEY DEFAULT 1,
    name                     VARCHAR(80)              NOT NULL,
    legal_name               VARCHAR(160)             NOT NULL,
    nif                      VARCHAR(14)              NOT NULL,
    email                    VARCHAR(160)             NOT NULL,
    phone                    VARCHAR(16)              NOT NULL,
    address                  VARCHAR(200)             NOT NULL,
    website                  VARCHAR(200)             NOT NULL,
    logo_key                 VARCHAR(200),
    logo_content_type        VARCHAR(40),
    logo_hash                VARCHAR(16),
    logo_original_name       VARCHAR(255),
    data_retention_months    INTEGER                  NOT NULL,
    consent_text_version     VARCHAR(20)              NOT NULL,
    consent_job_text         VARCHAR(1500)            NOT NULL,
    consent_talent_pool_text VARCHAR(1500)            NOT NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by               UUID,
    updated_by               UUID,
    CONSTRAINT ck_company_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_company_settings_retention CHECK (data_retention_months BETWEEN 6 AND 60),
    CONSTRAINT ck_company_settings_logo CHECK ((logo_key IS NULL) = (logo_hash IS NULL)),
    CONSTRAINT fk_company_settings_consent_version FOREIGN KEY (consent_text_version) REFERENCES consent_text_versions (version),
    CONSTRAINT fk_company_settings_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_company_settings_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);

-- Seed: values of the frontend (mocks/catalogs.ts)
INSERT INTO consent_text_versions (version, job_text, talent_pool_text, created_at) VALUES
('2026.1',
 'Autorizo a FEH OIRAM a tratar os meus dados pessoais para efeitos de recrutamento e seleção para esta vaga, incluindo a partilha com a empresa cliente, nos termos da Lei n.º 22/11.',
 'Autorizo a FEH OIRAM a manter os meus dados no banco de talentos durante 24 meses, para me contactar sobre futuras oportunidades.',
 now());

INSERT INTO company_settings (id, name, legal_name, nif, email, phone, address, website, data_retention_months,
                              consent_text_version, consent_job_text, consent_talent_pool_text, created_at, updated_at)
SELECT 1, 'FEH OIRAM', 'FEH OIRAM – Prestação de Serviços, Lda', '5417098263', 'geral@fehoiram.co.ao', '+244923000000',
       'Rua Rainha Ginga, Ingombota, Luanda', 'https://fehoiram.co.ao', 24,
       version, job_text, talent_pool_text, now(), now()
FROM consent_text_versions
WHERE version = '2026.1';
