#!/usr/bin/env bash
#
# actions-lint.sh — verify the GitHub Actions workflows under
# .github/workflows/ with actionlint, which checks YAML syntax, GH Actions
# semantics (job/step schema, expression syntax, action inputs) and embedded
# `run:` shell scripts (via shellcheck, when installed).
#
# No-op (pass) if .github/workflows/ doesn't exist or has no workflow files.
#
# Usage: scripts/actions-lint.sh
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

WORKFLOWS_DIR=".github/workflows"

bold=$(tput bold 2>/dev/null || true)
red=$(tput setaf 1 2>/dev/null || true)
green=$(tput setaf 2 2>/dev/null || true)
yellow=$(tput setaf 3 2>/dev/null || true)
reset=$(tput sgr0 2>/dev/null || true)

FAILED=()

section() { printf '\n%s==> %s%s\n' "$bold" "$1" "$reset"; }
have()    { command -v "$1" >/dev/null 2>&1; }

# --- GitHub Actions workflows -------------------------------------------------
lint_workflows() {
  section "GitHub Actions workflows (actionlint)"

  shopt -s nullglob
  local workflows=("$WORKFLOWS_DIR"/*.yml "$WORKFLOWS_DIR"/*.yaml)
  shopt -u nullglob

  if [[ ! -d "$WORKFLOWS_DIR" || ${#workflows[@]} -eq 0 ]]; then
    printf '%sOK%s no workflow files under %s\n' "$green" "$reset" "$WORKFLOWS_DIR"
    return
  fi

  if ! have actionlint; then
    printf '%sSKIP%s actionlint not found — install from https://github.com/rhysd/actionlint (brew install actionlint)\n' "$yellow" "$reset"
    FAILED+=("actions-lint (tool missing)")
    return
  fi

  if actionlint "${workflows[@]}"; then
    printf '%sOK%s workflow files\n' "$green" "$reset"
  else
    printf '%sFAIL%s workflow files\n' "$red" "$reset"
    FAILED+=("actions-lint")
  fi
}

lint_workflows

section "Summary"
if [[ ${#FAILED[@]} -eq 0 ]]; then
  printf '%sAll GitHub Actions checks passed.%s\n' "$green" "$reset"
  exit 0
fi
printf '%sFailed checks:%s %s\n' "$red" "$reset" "${FAILED[*]}"
exit 1
