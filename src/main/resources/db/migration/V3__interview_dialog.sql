ALTER TABLE interview.session
    ADD COLUMN plan_topics TEXT[];

ALTER TABLE interview.question
    ADD COLUMN kind  VARCHAR(16) NOT NULL DEFAULT 'MAIN',
    ADD COLUMN topic TEXT;

UPDATE interview.question
SET kind = 'FOLLOW_UP'
WHERE parent_question_id IS NOT NULL;

ALTER TABLE interview.question
    ADD CONSTRAINT chk_question_kind
        CHECK (kind IN ('MAIN', 'FOLLOW_UP', 'CLARIFICATION', 'REDIRECT')),
    ADD CONSTRAINT chk_question_kind_parent
        CHECK ((kind = 'MAIN') = (parent_question_id IS NULL));
