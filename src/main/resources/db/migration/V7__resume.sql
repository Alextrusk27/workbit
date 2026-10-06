CREATE SCHEMA IF NOT EXISTS resume;

CREATE TABLE IF NOT EXISTS resume.document (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name              VARCHAR(100) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    format            VARCHAR(8) NOT NULL,
    size_bytes        INTEGER NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_document_name CHECK (length(btrim(name)) BETWEEN 1 AND 100),
    CONSTRAINT chk_document_format CHECK (format IN ('PDF', 'DOCX', 'TXT')),
    CONSTRAINT chk_document_size CHECK (size_bytes BETWEEN 1 AND 5242880)
);

CREATE INDEX IF NOT EXISTS idx_document_user_created
    ON resume.document(user_id, created_at DESC);

ALTER TABLE training.question DROP COLUMN bank_question_id;

DROP TABLE content.question_bank;
