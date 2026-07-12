#!/usr/bin/env bash
#
# openapi-lint.sh — verify docs/api/openapi.yaml (the design-first REST contract)
# against the OpenAPI ruleset in .spectral.yaml using Spectral.
#
# Usage: scripts/openapi-lint.sh
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

OPENAPI_DOC="docs/api/openapi.yaml"
RULESET=".spectral.yaml"

bold=$(tput bold 2>/dev/null || true)
red=$(tput setaf 1 2>/dev/null || true)
green=$(tput setaf 2 2>/dev/null || true)
yellow=$(tput setaf 3 2>/dev/null || true)
reset=$(tput sgr0 2>/dev/null || true)

FAILED=()

section() { printf '\n%s==> %s%s\n' "$bold" "$1" "$reset"; }
have()    { command -v "$1" >/dev/null 2>&1; }

# --- OpenAPI contract ---------------------------------------------------------
lint_openapi() {
  section "OpenAPI contract (spectral)"
  if ! have spectral; then
    printf '%sSKIP%s spectral not found — install with: npm i -g @stoplight/spectral-cli\n' "$yellow" "$reset"
    FAILED+=("openapi-lint (tool missing)")
    return
  fi
  if spectral lint "$OPENAPI_DOC" --ruleset "$RULESET"; then
    printf '%sOK%s openapi contract\n' "$green" "$reset"
  else
    printf '%sFAIL%s openapi contract\n' "$red" "$reset"
    FAILED+=("openapi-lint")
  fi
}

lint_openapi

section "Summary"
if [[ ${#FAILED[@]} -eq 0 ]]; then
  printf '%sAll OpenAPI checks passed.%s\n' "$green" "$reset"
  exit 0
fi
printf '%sFailed checks:%s %s\n' "$red" "$reset" "${FAILED[*]}"
exit 1
