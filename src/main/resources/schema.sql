CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS interview;

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

CREATE TABLE IF NOT EXISTS interview.session (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    profession      VARCHAR(32) NOT NULL,
    company_type    VARCHAR(32) NOT NULL,
    level           VARCHAR(32) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    total_questions INT NOT NULL,
    created         TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,

    CONSTRAINT chk_session_profession
        CHECK (profession IN ('JAVA_DEV', 'PYTHON_DEV', 'QA')),
    CONSTRAINT chk_session_company_type
        CHECK (company_type IN ('BANK', 'FINTECH', 'STARTUP', 'PRODUCT', 'OUTSOURCE', 'GOV')),
    CONSTRAINT chk_session_level
        CHECK (level IN ('JUNIOR', 'MIDDLE', 'SENIOR', 'LEAD')),
    CONSTRAINT chk_session_status
        CHECK (status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED'))
);

CREATE TABLE IF NOT EXISTS interview.question (
    id            UUID PRIMARY KEY,
    session_id    UUID NOT NULL REFERENCES interview.session(id) ON DELETE CASCADE,
    category      VARCHAR(32) NOT NULL,
    question_text TEXT NOT NULL,
    order_index   INT NOT NULL,

    CONSTRAINT chk_question_category
        CHECK (category IN (
            'JAVA_CORE', 'CONCURRENCY', 'SPRING', 'SPRING_BOOT', 'SQL_JPA',
            'TRANSACTIONS', 'XML_SOAP', 'LEGACY_INTEGRATION',
            'PYTHON_CORE', 'ASYNCIO', 'DJANGO', 'FASTAPI', 'ORM_SQL', 'DATA_PROCESSING',
            'TEST_DESIGN', 'TEST_AUTOMATION', 'MANUAL_TESTING', 'API_TESTING', 'PERFORMANCE_TESTING',
            'REST_API', 'MICROSERVICES', 'DISTRIBUTED_SYSTEMS', 'CACHING', 'OBSERVABILITY',
            'NOSQL', 'SECURITY', 'CI_CD', 'COMPLIANCE', 'SOFT_SKILLS'
        )),

    CONSTRAINT chk_question_order_index
        CHECK (order_index BETWEEN 1 AND 20)
);

CREATE TABLE IF NOT EXISTS interview.answer (
    id           UUID PRIMARY KEY,
    question_id  UUID NOT NULL UNIQUE REFERENCES interview.question(id) ON DELETE CASCADE,
    answer_text  TEXT NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS interview.answer_feedback (
    id            UUID PRIMARY KEY,
    answer_id     UUID NOT NULL UNIQUE REFERENCES interview.answer(id) ON DELETE CASCADE,
    score         INT NOT NULL,
    feedback_text TEXT NOT NULL,
    generated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_feedback_score
        CHECK (score BETWEEN 1 AND 5)
);

CREATE TABLE IF NOT EXISTS interview.report (
    id                UUID PRIMARY KEY,
    session_id        UUID NOT NULL UNIQUE REFERENCES interview.session(id) ON DELETE CASCADE,
    total_score       NUMERIC(3,1) NOT NULL,
    offer_probability INT NOT NULL,
    overall_feedback  TEXT NOT NULL,
    generated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_report_total_score
        CHECK (total_score BETWEEN 1.0 AND 5.0),
    CONSTRAINT chk_report_offer_probability
        CHECK (offer_probability BETWEEN 0 AND 100)
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