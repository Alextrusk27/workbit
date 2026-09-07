ALTER TABLE interview.session
    ADD COLUMN closing_remark TEXT;

ALTER TABLE interview.session
    DROP COLUMN plan_topics;

ALTER TABLE interview.session
    ADD COLUMN plan_topics JSONB;
