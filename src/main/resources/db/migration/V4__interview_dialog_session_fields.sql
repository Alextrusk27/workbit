ALTER TABLE interview.session
    ADD COLUMN closing_remark TEXT;

ALTER TABLE interview.session
    DROP COLUMN plan_topics;

ALTER TABLE interview.session
    ADD COLUMN plan_topics JSONB;

ALTER TABLE interview.session
    ADD COLUMN asked_before TEXT;

ALTER TABLE vacancy.snapshot
    ADD COLUMN employer_logo_url TEXT;

-- Уровни тренажёра: четыре грейда (NOEXP / JUNIOR / MIDDLE / SENIOR) сведены к трём уровням
-- сложности (EASY / MEDIUM / HARD). NOEXP и JUNIOR сливаются в EASY, MIDDLE -> MEDIUM,
-- SENIOR -> HARD; после слияния в levels банка возможны дубли, поэтому массив собирается
-- заново через DISTINCT.

ALTER TABLE training.session
    DROP CONSTRAINT chk_session_level;

UPDATE training.session
SET level = CASE level
                WHEN 'NOEXP' THEN 'EASY'
                WHEN 'JUNIOR' THEN 'EASY'
                WHEN 'MIDDLE' THEN 'MEDIUM'
                WHEN 'SENIOR' THEN 'HARD'
                ELSE level
            END
WHERE level IN ('NOEXP', 'JUNIOR', 'MIDDLE', 'SENIOR');

ALTER TABLE training.session
    ADD CONSTRAINT chk_session_level
        CHECK (level IN ('EASY', 'MEDIUM', 'HARD'));

ALTER TABLE content.question_bank
    DROP CONSTRAINT chk_bank_levels;

UPDATE content.question_bank qb
SET levels = (
    SELECT array_agg(DISTINCT CASE l
                                  WHEN 'NOEXP' THEN 'EASY'
                                  WHEN 'JUNIOR' THEN 'EASY'
                                  WHEN 'MIDDLE' THEN 'MEDIUM'
                                  WHEN 'SENIOR' THEN 'HARD'
                                  ELSE l
                              END)::varchar[]
    FROM unnest(qb.levels) AS l
)
WHERE qb.levels && ARRAY['NOEXP', 'JUNIOR', 'MIDDLE', 'SENIOR']::varchar[];

ALTER TABLE content.question_bank
    ADD CONSTRAINT chk_bank_levels
        CHECK (levels <@ ARRAY['EASY', 'MEDIUM', 'HARD']::varchar[]
            AND cardinality(levels) >= 1);
