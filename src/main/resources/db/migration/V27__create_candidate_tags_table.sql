-- Tags of a candidate. Deleting a tag removes it from every candidate (decision D3-2)
CREATE TABLE candidate_tags (
    candidate_id UUID NOT NULL,
    tag_id       UUID NOT NULL,
    CONSTRAINT pk_candidate_tags PRIMARY KEY (candidate_id, tag_id),
    CONSTRAINT fk_candidate_tags_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id) ON DELETE CASCADE,
    CONSTRAINT fk_candidate_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
);

CREATE INDEX ix_candidate_tags_tag_id ON candidate_tags (tag_id);
