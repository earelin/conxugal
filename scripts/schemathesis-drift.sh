#!/usr/bin/env bash
#
# schemathesis-drift.sh — drift-check a running application instance against
# docs/api/openapi.yaml using Schemathesis (ADR-0010): fuzzes every documented
# GET /api/admin/** operation and fails if a response doesn't conform to the
# contract (undocumented status code, schema mismatch, etc).
#
# Expects the application (and the Postgres it talks to) to already be running
# externally, same convention as the `acceptance` Gradle module (ADR-0007):
#
#   cd server && docker compose up -d postgres && ./gradlew run
#
# Seeds one fixed ADMIN account directly into Postgres via psql — the two
# mutating admin operations (createUser, setUserEnabled) aren't implemented
# yet (FEAT-0004 TASK-0003) and there's no way for an API client to fetch a
# post-login CSRF token, so this run is scoped to GET operations only. Revisit
# once TASK-0003 lands and that gap is solved for real clients too.
#
# Usage: scripts/schemathesis-drift.sh
# Env overrides: APP_BASE_URL, PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

OPENAPI_DOC="docs/api/openapi.yaml"
APP_BASE_URL="${APP_BASE_URL:-http://localhost:8080}"

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-conxugal}"
PGUSER="${PGUSER:-conxugal}"
PGPASSWORD="${PGPASSWORD:-conxugal}"
export PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD

ADMIN_EMAIL="schemathesis-admin@conxugal.test"
ADMIN_PASSWORD="schemathesis-drift-test-only"

bold=$(tput bold 2>/dev/null || true)
red=$(tput setaf 1 2>/dev/null || true)
green=$(tput setaf 2 2>/dev/null || true)
yellow=$(tput setaf 3 2>/dev/null || true)
reset=$(tput sgr0 2>/dev/null || true)

section() { printf '\n%s==> %s%s\n' "$bold" "$1" "$reset"; }
have()    { command -v "$1" >/dev/null 2>&1; }
fail()    { printf '%sFAIL%s %s\n' "$red" "$reset" "$1"; exit 1; }

COOKIE_JAR=""
cleanup() { [[ -n "$COOKIE_JAR" ]] && rm -f "$COOKIE_JAR"; }
trap cleanup EXIT

# --- Preconditions -------------------------------------------------------------
section "Preconditions"
for tool in curl psql uvx uv; do
  have "$tool" || fail "$tool not found on PATH"
done

wait_for_app() {
  for _ in $(seq 1 15); do
    [[ "$(curl -s -o /dev/null -w '%{http_code}' "$APP_BASE_URL/login")" == "200" ]] && return 0
    sleep 1
  done
  return 1
}
if wait_for_app; then
  printf '%sOK%s application reachable at %s\n' "$green" "$reset" "$APP_BASE_URL"
else
  fail "application not reachable at $APP_BASE_URL (start it first: cd server && docker compose up -d postgres && ./gradlew run)"
fi

# --- Seed a known ADMIN account --------------------------------------------------
section "Seeding stable ADMIN fixture ($ADMIN_EMAIL)"
PASSWORD_HASH=$(ADMIN_PASSWORD="$ADMIN_PASSWORD" uv run --with argon2-cffi python3 - <<'PY'
import base64, os
from argon2 import low_level

salt = os.urandom(16)
password = os.environ["ADMIN_PASSWORD"].encode()

digest = low_level.hash_secret_raw(
    secret=password,
    salt=salt,
    time_cost=3,
    memory_cost=65536,
    parallelism=1,
    hash_len=32,
    type=low_level.Type.ID,
    version=19,
)
# Matches Argon2idPasswordEncoder's encoded form: memory:iterations:parallelism:salt:hash
print(f"65536:3:1:{base64.b64encode(salt).decode()}:{base64.b64encode(digest).decode()}")
PY
) || fail "could not compute the Argon2id password hash (is argon2-cffi resolvable via uv?)"

psql -v ON_ERROR_STOP=1 -q -c \
  "INSERT INTO users (email, password_hash, role) VALUES ('$ADMIN_EMAIL', '$PASSWORD_HASH', 'ADMIN')
   ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash, role = 'ADMIN';" \
  || fail "could not seed the ADMIN fixture into Postgres at $PGHOST:$PGPORT/$PGDATABASE"
printf '%sOK%s admin fixture seeded\n' "$green" "$reset"

# --- Log in and capture the session cookie --------------------------------------
section "Authenticating as $ADMIN_EMAIL"
COOKIE_JAR="$(mktemp)"
LOGIN_STATUS=$(curl -sS -c "$COOKIE_JAR" -o /dev/null -w '%{http_code}' \
  -X POST "$APP_BASE_URL/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
case "$LOGIN_STATUS" in
  200|302|303|307) ;;
  *) fail "login failed with HTTP $LOGIN_STATUS" ;;
esac
SESSION=$(awk -F'\t' '$6=="SESSION"{print $7}' "$COOKIE_JAR")
[[ -n "$SESSION" ]] || fail "no SESSION cookie returned by /login"
printf '%sOK%s session established\n' "$green" "$reset"

# --- Run Schemathesis ------------------------------------------------------------
section "Schemathesis drift check ($OPENAPI_DOC vs $APP_BASE_URL)"
printf '%sSKIP%s mutating operations (createUser, setUserEnabled) — not yet implemented and no CSRF-token-fetch mechanism exists for API clients (see header comment)\n' "$yellow" "$reset"
uvx schemathesis run "$OPENAPI_DOC" --url "$APP_BASE_URL" \
  --header "Cookie: SESSION=$SESSION" \
  --include-method GET
EXIT_CODE=$?

section "Summary"
if [[ $EXIT_CODE -eq 0 ]]; then
  printf '%sNo drift detected between %s and the running instance.%s\n' "$green" "$OPENAPI_DOC" "$reset"
else
  printf '%sDrift detected between %s and the running instance — see failures above.%s\n' "$red" "$OPENAPI_DOC" "$reset"
fi
exit "$EXIT_CODE"
