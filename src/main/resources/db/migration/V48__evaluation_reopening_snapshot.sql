-- Stage 9b adjustment: every reopening keeps a copy of the evaluation as it was submitted (audit; administrators and
-- recruiters only). The free texts of the copy are deleted on the candidate's erasure, the scores stay.

ALTER TABLE evaluation_reopenings ADD COLUMN previous_recommendation VARCHAR(10);
ALTER TABLE evaluation_reopenings ADD COLUMN previous_overall_score NUMERIC(2, 1);
ALTER TABLE evaluation_reopenings ADD COLUMN previous_strengths VARCHAR(800);
ALTER TABLE evaluation_reopenings ADD COLUMN previous_concerns VARCHAR(800);
ALTER TABLE evaluation_reopenings ADD COLUMN previous_notes VARCHAR(1500);
ALTER TABLE evaluation_reopenings ADD COLUMN previous_submitted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE evaluation_reopenings ADD CONSTRAINT ck_evaluation_reopenings_recommendation
    CHECK (previous_recommendation IS NULL OR previous_recommendation IN ('STRONG_YES', 'YES', 'NO', 'STRONG_NO'));

CREATE TABLE evaluation_reopening_scores (
    reopening_id UUID        NOT NULL,
    position     INTEGER     NOT NULL,
    competency   VARCHAR(60) NOT NULL,
    score        SMALLINT    NOT NULL,
    comment      VARCHAR(300),
    CONSTRAINT pk_evaluation_reopening_scores PRIMARY KEY (reopening_id, position),
    CONSTRAINT fk_evaluation_reopening_scores_reopening FOREIGN KEY (reopening_id) REFERENCES evaluation_reopenings (id) ON DELETE CASCADE,
    CONSTRAINT ck_evaluation_reopening_scores_score CHECK (score BETWEEN 0 AND 5)
);
