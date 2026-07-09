CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS interview;
CREATE SCHEMA IF NOT EXISTS vacancy;

CREATE TABLE IF NOT EXISTS auth.users (
    id             UUID PRIMARY KEY,
    email          VARCHAR(254) NOT NULL UNIQUE,
    pwd_hash       VARCHAR(255) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deactivated    TIMESTAMPTZ,

    CONSTRAINT chk_active_deactivated
        CHECK (
            (active = TRUE  AND deactivated IS NULL) OR
            (active = FALSE AND deactivated IS NOT NULL)
        )
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

CREATE TABLE IF NOT EXISTS interview.session (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    profession      VARCHAR(32),
    company_type    VARCHAR(32),
    level           VARCHAR(32),
    source          VARCHAR(32) NOT NULL DEFAULT 'CATALOG',
    vacancy_snapshot_id UUID REFERENCES vacancy.snapshot(id),
    status          VARCHAR(32) NOT NULL,
    total_questions INT NOT NULL,
    created         TIMESTAMPTZ NOT NULL,
    completed_at    TIMESTAMPTZ,

    CONSTRAINT chk_session_profession
        CHECK (profession IN ('JAVA_DEV', 'PYTHON_DEV', 'QA')),
    CONSTRAINT chk_session_company_type
        CHECK (company_type IN ('BANK', 'FINTECH', 'STARTUP', 'PRODUCT', 'OUTSOURCE', 'GOV')),
    CONSTRAINT chk_session_level
        CHECK (level IN ('JUNIOR', 'MIDDLE', 'SENIOR', 'LEAD')),
    CONSTRAINT chk_session_status
        CHECK (status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT chk_session_completed_at
        CHECK (status != 'COMPLETED' OR completed_at IS NOT NULL),
    CONSTRAINT chk_session_source
        CHECK (source IN ('CATALOG', 'VACANCY')),
    CONSTRAINT chk_session_source_fields
        CHECK (
            (source = 'CATALOG' AND profession IS NOT NULL AND company_type IS NOT NULL
                AND level IS NOT NULL AND vacancy_snapshot_id IS NULL)
         OR (source = 'VACANCY' AND profession IS NULL AND company_type IS NULL
                AND level IS NULL AND vacancy_snapshot_id IS NOT NULL)
        )
);

CREATE TABLE IF NOT EXISTS interview.question (
    id            UUID PRIMARY KEY,
    session_id    UUID NOT NULL REFERENCES interview.session(id) ON DELETE CASCADE,
    category      VARCHAR(32) NOT NULL,
    question_text TEXT NOT NULL,
    order_index   INT NOT NULL,
    answered      BOOL DEFAULT FALSE,
    answer_text   TEXT,
    answered_at   TIMESTAMPTZ,

    CONSTRAINT chk_question_category
        CHECK (category IN (
            'JAVA_CORE', 'CONCURRENCY', 'SPRING', 'SPRING_BOOT', 'SQL_JPA',
            'TRANSACTIONS', 'XML_SOAP', 'LEGACY_INTEGRATION',
            'PYTHON_CORE', 'ASYNCIO', 'DJANGO', 'FASTAPI', 'ORM_SQL', 'DATA_PROCESSING',
            'TEST_DESIGN', 'TEST_AUTOMATION', 'MANUAL_TESTING', 'API_TESTING', 'PERFORMANCE_TESTING',
            'REST_API', 'MICROSERVICES', 'DISTRIBUTED_SYSTEMS', 'CACHING', 'OBSERVABILITY',
            'NOSQL', 'SECURITY', 'CI_CD', 'COMPLIANCE', 'SOFT_SKILLS',
            'VACANCY'
        )),
    CONSTRAINT chk_question_order_index
        CHECK (order_index BETWEEN 1 AND 20),
    CONSTRAINT chk_answer_has_text_and_timestamp
        CHECK (NOT answered OR (answer_text IS NOT NULL AND answered_at IS NOT NULL))
);


CREATE TABLE IF NOT EXISTS interview.answer_feedback (
    id            UUID PRIMARY KEY,
    question_id   UUID NOT NULL UNIQUE REFERENCES interview.question(id) ON DELETE CASCADE,
    score         INT NOT NULL,
    feedback_text TEXT NOT NULL,
    generated_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_feedback_score
        CHECK (score BETWEEN 1 AND 5)
);

CREATE TABLE IF NOT EXISTS interview.question_bank (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category VARCHAR(32) NOT NULL,
    level    VARCHAR(32) NOT NULL,
    text     TEXT NOT NULL,

    CONSTRAINT chk_bank_category
        CHECK (category IN (
            'JAVA_CORE', 'CONCURRENCY', 'SPRING', 'SPRING_BOOT', 'SQL_JPA',
            'TRANSACTIONS', 'XML_SOAP', 'LEGACY_INTEGRATION',
            'PYTHON_CORE', 'ASYNCIO', 'DJANGO', 'FASTAPI', 'ORM_SQL', 'DATA_PROCESSING',
            'TEST_DESIGN', 'TEST_AUTOMATION', 'MANUAL_TESTING', 'API_TESTING', 'PERFORMANCE_TESTING',
            'REST_API', 'MICROSERVICES', 'DISTRIBUTED_SYSTEMS', 'CACHING', 'OBSERVABILITY',
            'NOSQL', 'SECURITY', 'CI_CD', 'COMPLIANCE', 'SOFT_SKILLS'
        )),
    CONSTRAINT chk_bank_level
        CHECK (level IN ('JUNIOR', 'MIDDLE', 'SENIOR', 'LEAD'))
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
    CONSTRAINT chk_offer_probability
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

CREATE INDEX IF NOT EXISTS idx_interview_session_user_id
    ON interview.session(user_id);
CREATE INDEX IF NOT EXISTS idx_interview_question_session_id
    ON interview.question(session_id);

CREATE INDEX IF NOT EXISTS idx_vacancy_snapshot_hh_id
    ON vacancy.snapshot(hh_vacancy_id);