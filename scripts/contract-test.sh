#!/usr/bin/env bash
#
# contract-test.sh — verify that a running instance conforms to docs/api/openapi.yaml
# (the design-first REST contract, ADR-0010) using Schemathesis (ADR-0021).
#
# Schemathesis reads the contract, generates requests for every operation it describes,
# and asserts the live responses match — status codes, content types, response schemas,
# and the absence of 5xx. A run that only warns fails too: see SCHEMATHESIS_WARNINGS below,
# and a run that leaves an operation untouched fails as well: see EXEMPT_OPERATIONS.
# Configuration (auth header, generation budget, the excluded operation, the rows generated
# requests are pointed at) lives in schemathesis.toml at the repository root.
#
# Expects the application already running and reachable, with its database migrated so the
# seeded administrator exists. A run consumes the fixtures it deletes, so restart the
# application between runs — the seed is repeatable and puts them back. Bring one up with:
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

# Every warning but one, because a warning fails this gate and so each has to mean something.
# The omission is validation-mismatch, which reports the share of generated bodies an operation
# refused: behind real validation and a uniqueness constraint that share is high by
# construction, and it moves with whatever rows earlier phases created — it named a different
# pair of operations on consecutive runs of the same suite. The genuine form of what it looks
# for, an API refusing a body the contract calls valid, belongs to the positive-data-acceptance
# check, which stays on.
SCHEMATHESIS_WARNINGS="missing_auth,missing_test_data,missing_deserializer,unused_openapi_auth"
SCHEMATHESIS_WARNINGS="${SCHEMATHESIS_WARNINGS},unsupported_regex,method_not_allowed"
SCHEMATHESIS_WARNINGS="${SCHEMATHESIS_WARNINGS},constants_extraction"

# The operations this gate accepts as never exercised. Every other operation the contract
# declares has to come back from the run as tested — a filter in schemathesis.toml that stops
# matching, or an operation added to the contract that the run never reaches, is otherwise
# invisible: it makes the suite prove less while it stays green.
#
# One entry per line, in the "METHOD /path" form the report names them by, each with the reason
# it cannot be reached. An entry the contract no longer declares fails the gate too, so the list
# cannot outlive what it excuses.
#
# GET /api/admin/metrics is an unbounded SSE stream, not a JSON body: the request never
# completes. It is turned off in schemathesis.toml and the contract records the same limitation
# on the operation itself.
EXEMPT_OPERATIONS="GET /api/admin/metrics"

BASE_URL="${CONTRACT_TEST_BASE_URL:-http://localhost:8080}"
ADMIN_EMAIL="${CONTRACT_TEST_ADMIN_EMAIL:-root@local}"
ADMIN_PASSWORD="${CONTRACT_TEST_ADMIN_PASSWORD:-secret}"

bold=$(tput bold 2>/dev/null || true)
red=$(tput setaf 1 2>/dev/null || true)
green=$(tput setaf 2 2>/dev/null || true)
yellow=$(tput setaf 3 2>/dev/null || true)
reset=$(tput sgr0 2>/dev/null || true)

# World-writable because the image runs as its own unprivileged user, whose uid matches
# neither a developer's nor the CI runner's.
REPORT_DIR=$(mktemp -d)
chmod 777 "$REPORT_DIR"
trap 'rm -rf "$REPORT_DIR"' EXIT
JUNIT_REPORT="${REPORT_DIR}/junit.xml"

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
    return 1
  fi

  # --network host so the container reaches an instance published on the host's
  # localhost, which is where both a local compose stack and the CI runner put it.
  #
  # Output is teed rather than left to the terminal: schemathesis exits 0 on a run that only
  # warns, and a warning here is not advisory. Each one says the generated requests stopped
  # short of an operation's real logic, which is the difference between an operation that
  # conformed and one that was never properly reached — a green run that proves less than it
  # appears to. Where a warning is inherent to an operation rather than a gap in the fixtures
  # it is turned off against that operation in schemathesis.toml, with the reason written
  # down, so anything left here is new.
  local output
  output=$(mktemp)
  local status=0
  docker run --rm --network host \
    --env CONTRACT_TEST_SESSION \
    --volume "${ROOT}/${OPENAPI_DOC}:/spec/openapi.yaml:ro" \
    --volume "${ROOT}/${CONFIG_FILE}:/spec/schemathesis.toml:ro" \
    --volume "${REPORT_DIR}:/report" \
    "$SCHEMATHESIS_IMAGE" \
    --config-file /spec/schemathesis.toml \
    run /spec/openapi.yaml --url "$BASE_URL" --warnings "$SCHEMATHESIS_WARNINGS" \
    --report junit --report-junit-path /report/junit.xml \
    2>&1 | tee "$output" || status=1

  if [[ $status -ne 0 ]]; then
    printf '%sFAIL%s contract conformance\n' "$red" "$reset"
    FAILED+=("contract-test")
  elif grep -qE '^ *⚠️|[0-9]+ warnings? in ' "$output"; then
    printf '%sFAIL%s contract conformance warned, which this gate treats as a failure\n' \
      "$red" "$reset"
    FAILED+=("contract-test (warnings)")
  else
    printf '%sOK%s contract conformance\n' "$green" "$reset"
  fi
  rm -f "$output"
}

# --- Contract coverage -------------------------------------------------------
# Conformance is only worth what it covers, and how much it covers is configuration: a
# schemathesis.toml filter, a phase, an operation the contract grew after this file was last
# read. The JUnit report names every operation the run actually reached, so the contract's own
# list of operations is held against it — anything declared but neither reached nor excused by
# EXEMPT_OPERATIONS fails, as does an excuse for an operation the contract no longer declares.
#
# The comparison runs inside the same pinned image, which already carries a Python and a YAML
# parser, so it adds nothing to what a contributor has to install.
check_coverage() {
  section "Contract coverage (every declared operation exercised)"

  if [[ ! -f "$JUNIT_REPORT" ]]; then
    printf '%sFAIL%s schemathesis wrote no report to compare the contract against\n' "$red" "$reset"
    FAILED+=("contract-coverage (no report)")
    return
  fi

  if docker run --rm --interactive --entrypoint python \
    --env CONTRACT_TEST_EXEMPT="$EXEMPT_OPERATIONS" \
    --volume "${ROOT}/${OPENAPI_DOC}:/spec/openapi.yaml:ro" \
    --volume "${REPORT_DIR}:/report:ro" \
    "$SCHEMATHESIS_IMAGE" - <<'PYTHON'
import os
import sys
import xml.etree.ElementTree as ElementTree

import yaml

METHODS = {"get", "put", "post", "delete", "options", "head", "patch", "trace"}

with open("/spec/openapi.yaml", encoding="utf-8") as handle:
    paths = yaml.safe_load(handle).get("paths") or {}
declared = {
    f"{method.upper()} {path}"
    for path, operations in paths.items()
    for method in operations
    if method.lower() in METHODS
}

exercised, skipped = set(), {}
for case in ElementTree.parse("/report/junit.xml").getroot().iter("testcase"):
    reason = case.find("skipped")
    if reason is None:
        exercised.add(case.get("name"))
    else:
        skipped[case.get("name")] = (reason.text or "skipped").strip()

exempt = {line.strip() for line in os.environ["CONTRACT_TEST_EXEMPT"].splitlines() if line.strip()}

uncovered = sorted(declared - exempt - exercised)
stale = sorted(exempt - declared)
for operation in uncovered:
    print(f"  {operation} — {skipped.get(operation, 'no test case: filtered out of the run')}")
for operation in stale:
    print(f"  {operation} — exempt, but the contract no longer declares it")
print(
    f"{len(exercised - exempt)} of {len(declared) - len(exempt)} operations exercised"
    f", {len(exempt)} exempt, {len(declared)} declared"
)
sys.exit(1 if uncovered or stale else 0)
PYTHON
  then
    printf '%sOK%s contract coverage\n' "$green" "$reset"
  else
    printf '%sFAIL%s the run left operations of the contract untouched\n' "$red" "$reset"
    FAILED+=("contract-coverage")
  fi
}

if log_in; then
  if run_schemathesis; then
    check_coverage
  fi
fi

section "Summary"
if [[ ${#FAILED[@]} -eq 0 ]]; then
  printf '%sThe running API conforms to %s.%s\n' "$green" "$OPENAPI_DOC" "$reset"
  exit 0
fi
printf '%sFailed checks:%s %s\n' "$red" "$reset" "${FAILED[*]}"
exit 1
