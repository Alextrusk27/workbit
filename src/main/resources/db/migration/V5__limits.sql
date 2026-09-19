ALTER TABLE billing.usage_event
    RENAME COLUMN target TO operation;

ALTER TABLE billing.usage_event
    ALTER COLUMN operation TYPE VARCHAR(32);

ALTER TABLE billing.usage_event
    DROP CONSTRAINT chk_usage_event_target;

UPDATE billing.usage_event
SET delta = delta * CASE operation WHEN 'INTERVIEW' THEN 20 ELSE 10 END;

UPDATE billing.usage_event
SET operation = CASE WHEN label = 'Подарок за покупку' THEN 'GIFT' ELSE 'PACK' END
WHERE kind = 'CREDIT';

CREATE TEMP TABLE pack_pairs AS
SELECT user_id,
       at,
       label,
       (array_agg(id ORDER BY id))[1] AS keep_id,
       sum(delta)                     AS total
FROM billing.usage_event
WHERE kind = 'CREDIT'
  AND operation = 'PACK'
GROUP BY user_id, at, label
HAVING count(*) > 1;

UPDATE billing.usage_event e
SET delta = p.total
FROM pack_pairs p
WHERE e.id = p.keep_id;

DELETE
FROM billing.usage_event e USING pack_pairs p
WHERE e.user_id = p.user_id
  AND e.at = p.at
  AND e.label = p.label
  AND e.kind = 'CREDIT'
  AND e.operation = 'PACK'
  AND e.id <> p.keep_id;

DROP TABLE pack_pairs;

ALTER TABLE billing.usage_event
    ADD CONSTRAINT chk_usage_event_operation
        CHECK (operation IN ('INTERVIEW', 'TRAINING', 'TRAINING_RESTART', 'TRAINING_MORE', 'REFERENCE_ANSWER',
                             'PACK', 'WELCOME', 'GIFT', 'EXPIRE'));

ALTER TABLE billing.account
    ADD COLUMN limits           INT NOT NULL DEFAULT 0,
    ADD COLUMN limits_expire_at TIMESTAMPTZ,
    ADD COLUMN paid_at           TIMESTAMPTZ;

UPDATE billing.account a
SET limits           = CASE
                            WHEN plan = 'FREE' THEN 20
                            WHEN plan_expires_at > now() THEN plan_interviews_left * 20 + plan_trainings_left * 10
                            ELSE 0
                        END,
    limits_expire_at = CASE
                            WHEN plan = 'FREE' THEN now() + INTERVAL '3 months'
                            WHEN plan_expires_at > now() THEN plan_expires_at
                        END,
    paid_at           = (SELECT min(p.paid_at)
                         FROM billing.payment p
                         WHERE p.user_id = a.user_id
                           AND p.status = 'PAID');

INSERT INTO billing.usage_event (id, user_id, at, kind, operation, delta, label)
SELECT gen_random_uuid(), user_id, now(), 'CREDIT', 'WELCOME', 20, 'Приветственные лимиты'
FROM billing.account
WHERE plan = 'FREE';

ALTER TABLE billing.account
    DROP CONSTRAINT chk_account_plan,
    DROP CONSTRAINT chk_account_left_non_negative,
    DROP CONSTRAINT chk_account_paid_plan_expires;

ALTER TABLE billing.account
    DROP COLUMN plan,
    DROP COLUMN plan_expires_at,
    DROP COLUMN plan_interviews_left,
    DROP COLUMN plan_trainings_left;

ALTER TABLE billing.account
    ADD CONSTRAINT chk_account_limits
        CHECK (limits >= 0 AND (limits = 0 OR limits_expire_at IS NOT NULL));

ALTER TABLE billing.payment
    DROP CONSTRAINT chk_payment_product;

ALTER TABLE billing.payment
    ADD CONSTRAINT chk_payment_product
        CHECK (product IN ('PLAN_PRO', 'PLAN_MAX', 'PACK_50', 'PACK_200', 'PACK_500'));

ALTER TABLE training.question
    ADD COLUMN reference_answer_unlocked_at TIMESTAMPTZ;

UPDATE training.question
SET reference_answer_unlocked_at = now();
