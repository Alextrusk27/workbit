DO $$
BEGIN
    IF '${email_hash_secret}' LIKE '$%' OR length('${email_hash_secret}') < 32 THEN
        RAISE EXCEPTION 'email_hash_secret is not set';
    END IF;
END $$;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS billing.welcome_grant (
    email_hash VARCHAR(64) PRIMARY KEY,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM auth.users WHERE email ~ '[^\x00-\x7F]') THEN
        RAISE EXCEPTION 'non-ascii email found, normalize it manually first';
    END IF;
END $$;

UPDATE auth.users
SET email = lower(email)
WHERE email <> lower(email);

INSERT INTO billing.welcome_grant (email_hash, granted_at)
SELECT encode(hmac(u.email, '${email_hash_secret}', 'sha256'), 'base64'), u.created
FROM auth.users u
WHERE EXISTS (SELECT 1 FROM billing.account a WHERE a.user_id = u.id)
ON CONFLICT DO NOTHING;

ALTER TABLE billing.payment
    DROP CONSTRAINT payment_user_id_fkey;
