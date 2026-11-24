-- Languages of a candidate (the frontend gives them no id: the list is replaced as a whole)
CREATE TABLE candidate_languages (
    id           UUID PRIMARY KEY,
    candidate_id UUID                     NOT NULL,
    position     INTEGER                  NOT NULL,
    language     VARCHAR(30)              NOT NULL,
    -- Lower case without accents ("Inglês" → "ingles"): comparisons and uniqueness
    language_key VARCHAR(30)              NOT NULL,
    level        VARCHAR(12)              NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT uk_candidate_languages UNIQUE (candidate_id, language_key),
    CONSTRAINT fk_candidate_languages_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id) ON DELETE CASCADE,
    CONSTRAINT fk_candidate_languages_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_candidate_languages_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_candidate_languages_level CHECK (level IN ('BASIC', 'INTERMEDIATE', 'ADVANCED', 'FLUENT', 'NATIVE'))
);
