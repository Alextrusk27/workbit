CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS vacancy;
CREATE SCHEMA IF NOT EXISTS content;
CREATE SCHEMA IF NOT EXISTS training;
CREATE SCHEMA IF NOT EXISTS interview;

CREATE TABLE IF NOT EXISTS auth.users (
    id                  UUID PRIMARY KEY,
    email               VARCHAR(254) NOT NULL UNIQUE,
    email_verified      BOOLEAN NOT NULL DEFAULT FALSE,
    created             TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen           TIMESTAMPTZ NOT NULL DEFAULT now(),
    deletion_warned_at  TIMESTAMPTZ,
    personal_data_consent_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS auth.refresh_token (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user_id
    ON auth.refresh_token(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_token_hash
    ON auth.refresh_token(token_hash);

CREATE TABLE IF NOT EXISTS auth.login_code (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    code_hash   VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    attempts    INT NOT NULL DEFAULT 0,
    used_at     TIMESTAMPTZ,
    created     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_login_code_user_id
    ON auth.login_code(user_id);

CREATE TABLE IF NOT EXISTS vacancy.snapshot (
    id            UUID PRIMARY KEY,
    source        VARCHAR(32),
    source_id     TEXT,
    url           TEXT,
    name          VARCHAR(255) NOT NULL,
    employer      VARCHAR(255),
    experience    VARCHAR(64),
    key_skills    TEXT[],
    description   TEXT NOT NULL,
    fetched_at    TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_snapshot_source_pair
        CHECK ((source IS NULL) = (url IS NULL) AND (source IS NULL) = (source_id IS NULL)),
    CONSTRAINT chk_snapshot_source
        CHECK (source IS NULL OR source IN ('HH'))
);

CREATE INDEX IF NOT EXISTS idx_snapshot_source_id
    ON vacancy.snapshot(source, source_id);

CREATE TABLE IF NOT EXISTS content.profession_dict (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    match_key   VARCHAR(100) NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'AUTO',
    usage_count INT NOT NULL DEFAULT 0,
    created     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_profession_dict_status
        CHECK (status IN ('AUTO', 'APPROVED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_profession_dict_match_key
    ON content.profession_dict (match_key);

CREATE TABLE IF NOT EXISTS content.skill_dict (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profession_id UUID NOT NULL REFERENCES content.profession_dict(id) ON DELETE CASCADE,
    name          VARCHAR(100) NOT NULL,
    match_key     VARCHAR(100) NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'AUTO',
    usage_count   INT NOT NULL DEFAULT 0,
    created       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_skill_dict_status
        CHECK (status IN ('AUTO', 'APPROVED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_skill_dict_profession_match_key
    ON content.skill_dict (profession_id, match_key);

CREATE INDEX IF NOT EXISTS idx_skill_dict_match_key
    ON content.skill_dict (match_key);

CREATE TABLE IF NOT EXISTS content.question_bank (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profession_id    UUID NOT NULL REFERENCES content.profession_dict(id) ON DELETE CASCADE,
    skill_id         UUID NOT NULL REFERENCES content.skill_dict(id) ON DELETE CASCADE,
    levels           VARCHAR(32)[] NOT NULL,
    text             TEXT NOT NULL,
    reference_answer TEXT,
    source           VARCHAR(16) NOT NULL DEFAULT 'CLAUDE',
    created          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_bank_levels
        CHECK (levels <@ ARRAY['NOEXP', 'JUNIOR', 'MIDDLE', 'SENIOR']::varchar[]
            AND cardinality(levels) >= 1),
    CONSTRAINT chk_bank_source
        CHECK (source IN ('CLAUDE', 'MANUAL'))
);

CREATE INDEX IF NOT EXISTS idx_question_bank_selector
    ON content.question_bank (profession_id, skill_id);

CREATE TABLE IF NOT EXISTS training.session (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    skill           VARCHAR(100) NOT NULL,
    profession      VARCHAR(100) NOT NULL,
    level           VARCHAR(32) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    created         TIMESTAMPTZ NOT NULL,
    completed_at    TIMESTAMPTZ,

    CONSTRAINT chk_session_level
        CHECK (level IN ('NOEXP', 'JUNIOR', 'MIDDLE', 'SENIOR')),
    CONSTRAINT chk_session_status
        CHECK (status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT chk_session_completed_at
        CHECK (status != 'COMPLETED' OR completed_at IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_session_user_id
    ON training.session(user_id);

CREATE TABLE IF NOT EXISTS training.question (
    id                 UUID PRIMARY KEY,
    session_id         UUID NOT NULL REFERENCES training.session(id) ON DELETE CASCADE,
    bank_question_id   UUID REFERENCES content.question_bank(id) ON DELETE SET NULL,
    text               TEXT NOT NULL,
    reference_answer   TEXT,
    order_index        INT NOT NULL,
    answered           BOOL DEFAULT FALSE,
    answer_text        TEXT,
    answered_at        TIMESTAMPTZ,

    CONSTRAINT chk_question_order_index
        CHECK (order_index BETWEEN 1 AND 50),
    CONSTRAINT chk_question_answer_has_text_and_timestamp
        CHECK (NOT answered OR (answer_text IS NOT NULL AND answered_at IS NOT NULL)),
    CONSTRAINT uq_question_order
        UNIQUE (session_id, order_index)
);

CREATE INDEX IF NOT EXISTS idx_question_session_id
    ON training.question(session_id);
CREATE INDEX IF NOT EXISTS idx_question_bank_question_id
    ON training.question(bank_question_id);

CREATE TABLE IF NOT EXISTS training.feedback (
    id            UUID PRIMARY KEY,
    question_id   UUID NOT NULL UNIQUE REFERENCES training.question(id) ON DELETE CASCADE,
    score         INT NOT NULL,
    text          TEXT NOT NULL,
    generated_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_feedback_score
        CHECK (score BETWEEN 1 AND 5)
);

CREATE TABLE IF NOT EXISTS training.report (
    id                UUID PRIMARY KEY,
    session_id        UUID NOT NULL UNIQUE REFERENCES training.session(id) ON DELETE CASCADE,
    avg_score         DOUBLE PRECISION NOT NULL,
    overall_feedback  TEXT NOT NULL,
    generated_at      TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_report_avg_score
        CHECK (avg_score BETWEEN 1.0 AND 5.0)
);

CREATE TABLE IF NOT EXISTS interview.session (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    vacancy_snapshot_id UUID NOT NULL REFERENCES vacancy.snapshot(id),
    status              VARCHAR(32) NOT NULL,
    total_questions     INT NOT NULL,
    created             TIMESTAMPTZ NOT NULL,
    completed_at        TIMESTAMPTZ,

    CONSTRAINT chk_session_status
        CHECK (status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT chk_session_completed_at
        CHECK (status != 'COMPLETED' OR completed_at IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_session_user_id
    ON interview.session(user_id);

CREATE TABLE IF NOT EXISTS interview.question (
    id                 UUID PRIMARY KEY,
    session_id         UUID NOT NULL REFERENCES interview.session(id) ON DELETE CASCADE,
    parent_question_id UUID REFERENCES interview.question(id) ON DELETE CASCADE,
    text               TEXT NOT NULL,
    order_index        INT NOT NULL,
    follow_up          BOOLEAN NOT NULL DEFAULT FALSE,
    follow_up_checked  BOOLEAN NOT NULL DEFAULT FALSE,
    answered           BOOL DEFAULT FALSE,
    answer_text        TEXT,
    answered_at        TIMESTAMPTZ,

    CONSTRAINT chk_question_order_index
        CHECK (order_index BETWEEN 1 AND 20),
    CONSTRAINT chk_question_answer_has_text_and_timestamp
        CHECK (NOT answered OR (answer_text IS NOT NULL AND answered_at IS NOT NULL)),
    CONSTRAINT chk_question_follow_up_parent
        CHECK (follow_up = (parent_question_id IS NOT NULL)),
    CONSTRAINT uq_question_order
        UNIQUE NULLS NOT DISTINCT (session_id, parent_question_id, order_index)
);

CREATE INDEX IF NOT EXISTS idx_question_session_id
    ON interview.question(session_id);

CREATE TABLE IF NOT EXISTS interview.feedback (
    id            UUID PRIMARY KEY,
    question_id   UUID NOT NULL UNIQUE REFERENCES interview.question(id) ON DELETE CASCADE,
    score         INT NOT NULL,
    text          TEXT NOT NULL,
    generated_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_feedback_score
        CHECK (score BETWEEN 1 AND 5)
);

CREATE TABLE IF NOT EXISTS interview.report (
    id                UUID PRIMARY KEY,
    session_id        UUID NOT NULL UNIQUE REFERENCES interview.session(id) ON DELETE CASCADE,
    avg_score         DOUBLE PRECISION NOT NULL,
    offer_probability VARCHAR(32) NOT NULL,
    overall_feedback  TEXT NOT NULL,
    recommendations   TEXT,
    weakest_skill     VARCHAR(100),
    generated_at      TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_report_avg_score
        CHECK (avg_score BETWEEN 1.0 AND 5.0),
    CONSTRAINT chk_report_offer_probability
        CHECK (offer_probability IN ('LOW', 'MEDIUM', 'HIGH'))
);

INSERT INTO content.profession_dict (name, match_key, status) VALUES
    ('Java-разработчик', 'java разработчик', 'APPROVED'),
    ('Python-разработчик', 'python разработчик', 'APPROVED'),
    ('Инженер по тестированию', 'инженер тестированию', 'APPROVED')
ON CONFLICT DO NOTHING;
