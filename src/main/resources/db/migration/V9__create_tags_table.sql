-- Tags. Names are unique ignoring case. Link tables (jobs, candidates, applications) will reference
-- tags with ON DELETE CASCADE, so deleting a tag removes it from every record.
CREATE TABLE tags (
    id         UUID PRIMARY KEY,
    name       VARCHAR(40)              NOT NULL,
    color      VARCHAR(20)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_tags_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_tags_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_tags_color CHECK (color IN ('INDIGO', 'CYAN', 'PURPLE', 'SUCCESS', 'WARNING', 'DANGER', 'MUTED'))
);

CREATE UNIQUE INDEX uk_tags_name ON tags (lower(name));

-- Seed: the tags of the frontend (mocks/catalogs.ts)
INSERT INTO tags (id, name, color, created_at, updated_at) VALUES
(gen_random_uuid(), 'Prioritário', 'DANGER', now(), now()),
(gen_random_uuid(), 'Talento', 'PURPLE', now(), now()),
(gen_random_uuid(), 'Bilingue', 'CYAN', now(), now()),
(gen_random_uuid(), 'Experiência offshore', 'INDIGO', now(), now()),
(gen_random_uuid(), 'Disponível já', 'SUCCESS', now(), now()),
(gen_random_uuid(), 'Aceita mudar de província', 'WARNING', now(), now()),
(gen_random_uuid(), 'Ex-colaborador', 'MUTED', now(), now()),
(gen_random_uuid(), 'Indicação', 'CYAN', now(), now());
