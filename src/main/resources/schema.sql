CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS training;
CREATE SCHEMA IF NOT EXISTS interview;
CREATE SCHEMA IF NOT EXISTS vacancy;
CREATE SCHEMA IF NOT EXISTS content;

CREATE TABLE IF NOT EXISTS auth.users (
    id                  UUID PRIMARY KEY,
    email               VARCHAR(254) NOT NULL UNIQUE,
    pwd_hash            VARCHAR(255) NOT NULL,
    email_verified      BOOLEAN NOT NULL DEFAULT FALSE,
    created             TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen           TIMESTAMPTZ NOT NULL DEFAULT now(),
    deletion_warned_at  TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS auth.refresh_token (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS auth.verification_token (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    type        VARCHAR(32) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_type
        CHECK (type IN ('PASSWORD_RESET', 'EMAIL_VERIFICATION'))
);

CREATE TABLE IF NOT EXISTS vacancy.snapshot (
    id            UUID PRIMARY KEY,
    hh_vacancy_id BIGINT,
    url           TEXT,
    name          VARCHAR(255) NOT NULL,
    employer      VARCHAR(255),
    experience    VARCHAR(64),
    key_skills    TEXT[],
    description   TEXT NOT NULL,
    fetched_at    TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_snapshot_hh_pair
        CHECK ((hh_vacancy_id IS NULL) = (url IS NULL))
);

CREATE TABLE IF NOT EXISTS content.profession_dict (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'AUTO',
    usage_count INT NOT NULL DEFAULT 0,
    created     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_profession_dict_status
        CHECK (status IN ('AUTO', 'APPROVED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_profession_dict_name
    ON content.profession_dict (lower(name));

CREATE TABLE IF NOT EXISTS content.topic_dict (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profession_id UUID NOT NULL REFERENCES content.profession_dict(id) ON DELETE CASCADE,
    name          VARCHAR(100) NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'AUTO',
    usage_count   INT NOT NULL DEFAULT 0,
    created       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_topic_dict_status
        CHECK (status IN ('AUTO', 'APPROVED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_topic_dict_profession_name
    ON content.topic_dict (profession_id, lower(name));

CREATE TABLE IF NOT EXISTS content.question_bank (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profession_id    UUID NOT NULL REFERENCES content.profession_dict(id) ON DELETE CASCADE,
    topic_id         UUID REFERENCES content.topic_dict(id) ON DELETE CASCADE,
    levels           VARCHAR(32)[] NOT NULL,
    text             TEXT NOT NULL,
    reference_answer TEXT,
    source           VARCHAR(16) NOT NULL DEFAULT 'CLAUDE',
    created          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_bank_levels
        CHECK (levels <@ ARRAY['JUNIOR', 'MIDDLE', 'SENIOR']::varchar[]
            AND cardinality(levels) >= 1),
    CONSTRAINT chk_bank_source
        CHECK (source IN ('CLAUDE', 'MANUAL'))
);

CREATE INDEX IF NOT EXISTS idx_question_bank_selector
    ON content.question_bank (profession_id, topic_id);

CREATE TABLE IF NOT EXISTS training.session (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    profession      VARCHAR(100) NOT NULL,
    topic           VARCHAR(100),
    level           VARCHAR(32) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    created         TIMESTAMPTZ NOT NULL,
    completed_at    TIMESTAMPTZ,

    CONSTRAINT chk_session_level
        CHECK (level IN ('JUNIOR', 'MIDDLE', 'SENIOR')),
    CONSTRAINT chk_session_status
        CHECK (status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT chk_session_completed_at
        CHECK (status != 'COMPLETED' OR completed_at IS NOT NULL)
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

CREATE TABLE IF NOT EXISTS training.question (
    id                 UUID PRIMARY KEY,
    session_id         UUID NOT NULL REFERENCES training.session(id) ON DELETE CASCADE,
    parent_question_id UUID REFERENCES training.question(id) ON DELETE CASCADE,
    bank_question_id   UUID REFERENCES content.question_bank(id) ON DELETE SET NULL,
    text               TEXT NOT NULL,
    order_index        INT NOT NULL,
    follow_up          BOOLEAN NOT NULL DEFAULT FALSE,
    follow_up_checked  BOOLEAN NOT NULL DEFAULT FALSE,
    answered           BOOL DEFAULT FALSE,
    answer_text        TEXT,
    answered_at        TIMESTAMPTZ,

    CONSTRAINT chk_question_order_index
        CHECK (order_index BETWEEN 1 AND 50),
    CONSTRAINT chk_question_answer_has_text_and_timestamp
        CHECK (NOT answered OR (answer_text IS NOT NULL AND answered_at IS NOT NULL)),
    CONSTRAINT chk_question_follow_up_parent
        CHECK (follow_up = (parent_question_id IS NOT NULL)),
    CONSTRAINT uq_question_order
        UNIQUE NULLS NOT DISTINCT (session_id, parent_question_id, order_index)
);

CREATE TABLE IF NOT EXISTS interview.question (
    id            UUID PRIMARY KEY,
    session_id    UUID NOT NULL REFERENCES interview.session(id) ON DELETE CASCADE,
    text          TEXT NOT NULL,
    order_index   INT NOT NULL,
    answered      BOOL DEFAULT FALSE,
    answer_text   TEXT,
    answered_at   TIMESTAMPTZ,

    CONSTRAINT chk_question_order_index
        CHECK (order_index BETWEEN 1 AND 20),
    CONSTRAINT chk_question_answer_has_text_and_timestamp
        CHECK (NOT answered OR (answer_text IS NOT NULL AND answered_at IS NOT NULL))
);


CREATE TABLE IF NOT EXISTS training.feedback (
    id            UUID PRIMARY KEY,
    question_id   UUID NOT NULL UNIQUE REFERENCES training.question(id) ON DELETE CASCADE,
    score         INT NOT NULL,
    text          TEXT NOT NULL,
    generated_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_feedback_score
        CHECK (score BETWEEN 1 AND 5)
);

CREATE TABLE IF NOT EXISTS interview.feedback (
    id            UUID PRIMARY KEY,
    question_id   UUID NOT NULL UNIQUE REFERENCES interview.question(id) ON DELETE CASCADE,
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

CREATE TABLE IF NOT EXISTS interview.report (
    id                UUID PRIMARY KEY,
    session_id        UUID NOT NULL UNIQUE REFERENCES interview.session(id) ON DELETE CASCADE,
    avg_score         DOUBLE PRECISION NOT NULL,
    offer_probability VARCHAR(32) NOT NULL,
    overall_feedback  TEXT NOT NULL,
    generated_at      TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_report_avg_score
        CHECK (avg_score BETWEEN 1.0 AND 5.0),
    CONSTRAINT chk_report_offer_probability
        CHECK (offer_probability IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user_id
    ON auth.refresh_token(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_token_hash
    ON auth.refresh_token(token_hash);

CREATE INDEX IF NOT EXISTS idx_verification_token_user_id
    ON auth.verification_token(user_id);
CREATE INDEX IF NOT EXISTS idx_verification_token_token_hash
    ON auth.verification_token(token_hash);

CREATE INDEX IF NOT EXISTS idx_session_user_id
    ON training.session(user_id);
CREATE INDEX IF NOT EXISTS idx_question_session_id
    ON training.question(session_id);
CREATE INDEX IF NOT EXISTS idx_question_bank_question_id
    ON training.question(bank_question_id);

CREATE INDEX IF NOT EXISTS idx_session_user_id
    ON interview.session(user_id);
CREATE INDEX IF NOT EXISTS idx_question_session_id
    ON interview.question(session_id);

CREATE INDEX IF NOT EXISTS idx_snapshot_hh_id
    ON vacancy.snapshot(hh_vacancy_id);

INSERT INTO content.profession_dict (name, status) VALUES
    ('Java-разработчик', 'APPROVED'),
    ('Python-разработчик', 'APPROVED'),
    ('Инженер по тестированию', 'APPROVED')
ON CONFLICT DO NOTHING;