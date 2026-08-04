#!/usr/bin/env bash
#
# contract-test.sh — verify that a running instance conforms to docs/api/openapi.yaml
# (the design-first REST contract, ADR-0010) using Schemathesis (ADR-0021).
#
# Schemathesis reads the contract, generates requests for every operation it describes,
# and asserts the live responses match — status codes, content types, response schemas,
# and the absence of 5xx. Configuration (auth header, generation budget, the two excluded
# operations) lives in schemathesis.toml at the repository root.
#
# Expects the application already running and reachable, with its database migrated so
# the seeded administrator exists. Bring one up with:
#
#   cd server && ./gradlew :application:dockerBuild && docker compose --profile app up -d --wait
#
# Usage: scripts/contract-test.sh
#
# Environment:
#   CONTRACT_TEST_BASE_URL         default http://localhost:8080
#   CONTRACT_TEST_ADMIN_EMAIL      default root@local
#   CONTRACT_TEST_ADMIN_PASSWORD   default secret
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

OPENAPI_DOC="docs/api/openapi.yaml"
CONFIG_FILE="schemathesis.toml"

# Bumped by hand — Dependabot reads manifests, not shell scripts.
SCHEMATHESIS_IMAGE="ghcr.io/schemathesis/schemathesis:4.24.3"

BASE_URL="${CONTRACT_TEST_BASE_URL:-http://localhost:8080}"
ADMIN_EMAIL="${CONTRACT_TEST_ADMIN_EMAIL:-root@local}"
ADMIN_PASSWORD="${CONTRACT_TEST_ADMIN_PASSWORD:-secret}"

bold=$(tput bold 2>/dev/null || true)
red=$(tput setaf 1 2>/dev/null || true)
green=$(tput setaf 2 2>/dev/null || true)
yellow=$(tput setaf 3 2>/dev/null || true)
reset=$(tput sgr0 2>/dev/null || true)

FAILED=()

section() { printf '\n%s==> %s%s\n' "$bold" "$1" "$reset"; }
have()    { command -v "$1" >/dev/null 2>&1; }

# --- Session -----------------------------------------------------------------
# The contract covers /api/** only; the login form that establishes the session is a
# server-rendered flow outside it (ADR-0005), so the cookie is obtained here rather than
# by Schemathesis. A successful login answers with a redirect, and it is that response
# which carries the cookie — hence no --location. JSON writes need no CSRF token: the
# CSRF filter only rejects form-encoded bodies.
log_in() {
  section "Session for ${ADMIN_EMAIL} (curl)"

  if ! have curl; then
    printf '%sSKIP%s curl not found\n' "$yellow" "$reset"
    FAILED+=("contract-test (tool missing)")
    return 1
  fi

  local headers
  headers=$(curl --silent --show-error --dump-header - --output /dev/null \
    --header 'Content-Type: application/json' \
    --header 'Accept: application/json' \
    --data "$(printf '{"username":"%s","password":"%s"}' "$ADMIN_EMAIL" "$ADMIN_PASSWORD")" \
    "${BASE_URL}/login")

  CONTRACT_TEST_SESSION=$(printf '%s' "$headers" \
    | tr -d '\r' \
    | sed -n 's/^[Ss]et-[Cc]ookie: SESSION=\([^;]*\).*$/\1/p' \
    | head -n 1)

  if [[ -z "$CONTRACT_TEST_SESSION" ]]; then
    printf '%sFAIL%s no session cookie — is %s running with its migrations applied?\n' \
      "$red" "$reset" "$BASE_URL"
    FAILED+=("contract-test (login refused)")
    return 1
  fi

  export CONTRACT_TEST_SESSION
  printf '%sOK%s session established\n' "$green" "$reset"
}

# --- Contract conformance ----------------------------------------------------
run_schemathesis() {
  section "API contract conformance (schemathesis)"

  if ! have docker; then
    printf '%sSKIP%s docker not found — install from https://docs.docker.com/engine/install/\n' "$yellow" "$reset"
    FAILED+=("contract-test (tool missing)")
    return
  fi

  # --network host so the container reaches an instance published on the host's
  # localhost, which is where both a local compose stack and the CI runner put it.
  if docker run --rm --network host \
    --env CONTRACT_TEST_SESSION \
    --volume "${ROOT}/${OPENAPI_DOC}:/spec/openapi.yaml:ro" \
    --volume "${ROOT}/${CONFIG_FILE}:/spec/schemathesis.toml:ro" \
    "$SCHEMATHESIS_IMAGE" \
    --config-file /spec/schemathesis.toml \
    run /spec/openapi.yaml --url "$BASE_URL"; then
    printf '%sOK%s contract conformance\n' "$green" "$reset"
  else
    printf '%sFAIL%s contract conformance\n' "$red" "$reset"
    FAILED+=("contract-test")
  fi
}

if log_in; then
  run_schemathesis
fi

section "Summary"
if [[ ${#FAILED[@]} -eq 0 ]]; then
  printf '%sThe running API conforms to %s.%s\n' "$green" "$OPENAPI_DOC" "$reset"
  exit 0
fi
printf '%sFailed checks:%s %s\n' "$red" "$reset" "${FAILED[*]}"
exit 1
