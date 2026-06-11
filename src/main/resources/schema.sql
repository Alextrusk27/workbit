CREATE TABLE IF NOT EXISTS users (
    id             UUID PRIMARY KEY,
    email          VARCHAR(254) NOT NULL UNIQUE,
    last_name      VARCHAR(100),
    first_name     VARCHAR(100),
    middle_name    VARCHAR(100),
    pwd_hash       VARCHAR(255) NOT NULL,
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deactivated    TIMESTAMPTZ,

    CONSTRAINT chk_active_deactivated
        CHECK (
            (active = TRUE  AND deactivated IS NULL) OR
            (active = FALSE AND deactivated IS NOT NULL)
        )
);