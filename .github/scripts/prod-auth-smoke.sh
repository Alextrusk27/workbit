#!/usr/bin/env bash
set -euo pipefail

# Аутентифицированный смок прода: подсаживает код входа смок-юзеру по SSH,
# логинится им и проверяет залогиненные ручки. С LLM_CHECK=1 дополнительно
# дёргает /training/normalize — живой вызов агента input-normalizer, то есть
# контрактная проверка всей LLM-цепочки (ключ, promptId, ответ, парсинг).
# Вход: SSH_DEST (user@host), SSH_KEY (путь к ключу);
# опционально BASE (default https://workbit.ru), LLM_CHECK (default 0).

BASE="${BASE:-https://workbit.ru}"
EMAIL='ci-smoke@example.com'
JAR=$(mktemp)
trap 'rm -f "$JAR"' EXIT

CODE=$(ssh -i "$SSH_KEY" "$SSH_DEST" bash -s <<'EOSSH'
set -euo pipefail
cd /opt/workbit
email='ci-smoke@example.com'
uid=$(docker compose -f compose.prod.yml exec -T postgres \
  sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atq' <<SQL
INSERT INTO auth.users (id, email, email_verified, personal_data_consent_at)
VALUES (gen_random_uuid(), '$email', true, now())
ON CONFLICT (email) DO NOTHING;
SELECT id FROM auth.users WHERE email = '$email';
SQL
)
[ -n "$uid" ] || { echo "no smoke user id" >&2; exit 1; }
code=$(printf '%06d' "$(shuf -i 0-999999 -n1)")
hash=$(printf '%s' "$uid:$code" | openssl dgst -sha256 -binary | base64 | tr '+/' '-_' | tr -d '=')
docker compose -f compose.prod.yml exec -T postgres \
  sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atq' <<SQL >/dev/null
UPDATE auth.login_code SET used_at = now() WHERE user_id = '$uid' AND used_at IS NULL;
INSERT INTO auth.login_code (id, user_id, code_hash, expires_at)
VALUES (gen_random_uuid(), '$uid', '$hash', now() + interval '15 minutes');
SQL
echo "$code"
EOSSH
)
[ -n "$CODE" ] || { echo "failed to seed a login code"; exit 1; }

expect() {
  want="$1"; label="$2"; shift 2
  got=$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR" -c "$JAR" "$@")
  if [ "$got" != "$want" ]; then
    echo "$label answered $got instead of $want"
    exit 1
  fi
  echo "$label -> $got"
}

expect 200 "verify-code" -X POST "$BASE/api/v1/auth/verify-code" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"code\":\"$CODE\"}"
expect 200 "auth/me" "$BASE/api/v1/auth/me"
expect 200 "billing/quota" "$BASE/api/v1/billing/quota"
expect 200 "interview/vacancies" "$BASE/api/v1/interview/vacancies"

if [ "${LLM_CHECK:-0}" = "1" ]; then
  ok=""
  for attempt in 1 2; do
    resp=$(curl -s -w '\n%{http_code}' -b "$JAR" -c "$JAR" \
      -X POST "$BASE/api/v1/training/normalize" \
      -H 'Content-Type: application/json' \
      -d '{"skill":"многапоточность ява","profession":"Java-разработчик"}')
    got=${resp##*$'\n'}
    json=${resp%$'\n'*}
    if [ "$got" = "200" ] && echo "$json" | jq -e 'has("skillRecognized")' >/dev/null; then
      echo "normalize -> 200, input-normalizer contract OK"
      ok=1
      break
    fi
    echo "normalize attempt $attempt failed: HTTP $got"
    sleep 10
  done
  [ -n "$ok" ] || { echo "input-normalizer smoke failed"; exit 1; }
fi

curl -s -o /dev/null -X POST -b "$JAR" -c "$JAR" "$BASE/api/v1/auth/logout" || true
echo "auth smoke passed"
