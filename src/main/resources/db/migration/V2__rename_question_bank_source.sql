ALTER TABLE content.question_bank
    DROP CONSTRAINT IF EXISTS chk_bank_source;

UPDATE content.question_bank
SET source = 'GENERATED'
WHERE source = 'CLAUDE';

ALTER TABLE content.question_bank
    ALTER COLUMN source SET DEFAULT 'GENERATED';

ALTER TABLE content.question_bank
    ADD CONSTRAINT chk_bank_source
        CHECK (source IN ('GENERATED', 'MANUAL'));
