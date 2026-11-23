-- Rejection reasons: never deleted (deactivated). The code uses client, withdrew and declined-offer.
CREATE TABLE rejection_reasons (
    id         UUID PRIMARY KEY,
    code       VARCHAR(60)              NOT NULL,
    label      VARCHAR(120)             NOT NULL,
    category   VARCHAR(20)              NOT NULL,
    active     BOOLEAN                  NOT NULL DEFAULT TRUE,
    position   INTEGER                  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uk_rejection_reasons_code UNIQUE (code),
    CONSTRAINT fk_rejection_reasons_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_rejection_reasons_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_rejection_reasons_category CHECK (category IN ('COMPANY', 'CANDIDATE', 'CLIENT'))
);

-- Seed: the reasons of the frontend (mocks/catalogs.ts), code = frontend id without "rr-"
INSERT INTO rejection_reasons (id, code, label, category, position, created_at, updated_at) VALUES
(gen_random_uuid(), 'requirements', 'Não cumpre os requisitos obrigatórios', 'COMPANY', 1, now(), now()),
(gen_random_uuid(), 'experience', 'Experiência insuficiente', 'COMPANY', 2, now(), now()),
(gen_random_uuid(), 'eliminatory', 'Respondeu negativamente a pergunta eliminatória', 'COMPANY', 3, now(), now()),
(gen_random_uuid(), 'salary', 'Pretensão salarial acima do orçamento', 'COMPANY', 4, now(), now()),
(gen_random_uuid(), 'location', 'Localização incompatível', 'COMPANY', 5, now(), now()),
(gen_random_uuid(), 'test', 'Não atingiu a nota mínima no teste', 'COMPANY', 6, now(), now()),
(gen_random_uuid(), 'interview', 'Desempenho insuficiente na entrevista', 'COMPANY', 7, now(), now()),
(gen_random_uuid(), 'other-selected', 'Outro candidato selecionado', 'COMPANY', 8, now(), now()),
(gen_random_uuid(), 'client', 'Rejeitado pelo cliente', 'CLIENT', 9, now(), now()),
(gen_random_uuid(), 'no-show', 'Não compareceu à entrevista', 'CANDIDATE', 10, now(), now()),
(gen_random_uuid(), 'declined-offer', 'Recusou a proposta', 'CANDIDATE', 11, now(), now()),
(gen_random_uuid(), 'withdrew', 'Desistiu do processo', 'CANDIDATE', 12, now(), now());
