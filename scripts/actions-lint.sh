#!/usr/bin/env bash
#
# actions-lint.sh — verify the GitHub Actions workflows under .github/workflows/
# and the shell scripts they invoke, with three complementary tools:
#
#   actionlint — YAML syntax, GH Actions semantics (job/step schema, expression
#     syntax, action inputs) and embedded `run:` shell scripts (via shellcheck,
#     when installed).
#   scripts/*.sh — linted with shellcheck. actionlint only reaches the shell
#     embedded in a workflow, so the scripts those `run:` steps call, including
#     this one, were previously linted by nothing.
#   zizmor — supply-chain and privilege auditing: unpinned action references,
#     persisted checkout credentials, over-broad GITHUB_TOKEN scopes, template
#     injection. Also audits .github/dependabot.yml, which actionlint ignores.
#
# The workflow checks are a no-op (pass) if .github/workflows/ doesn't exist or
# has no workflow files.
#
# Usage: scripts/actions-lint.sh
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

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
    printf '%sSKIP%s actionlint not found — run: mise install (https://github.com/rhysd/actionlint)\n' "$yellow" "$reset"
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

# --- GitHub Actions security --------------------------------------------------
audit_workflows() {
  section "GitHub Actions security (zizmor)"

  if ! have zizmor; then
    printf '%sSKIP%s zizmor not found — run: mise install (https://docs.zizmor.sh)\n' "$yellow" "$reset"
    FAILED+=("actions-security (tool missing)")
    return
  fi

  # zizmor discovers the workflows and dependabot.yml itself, and picks up
  # GH_TOKEN when set to enable the audits that need to resolve action refs
  # upstream (impostor-commit, stale-action-refs).
  if zizmor .; then
    printf '%sOK%s workflow security\n' "$green" "$reset"
  else
    printf '%sFAIL%s workflow security\n' "$red" "$reset"
    FAILED+=("actions-security")
  fi
}

# --- Shell scripts ------------------------------------------------------------
# actionlint only reaches shellcheck for `run:` blocks embedded in workflows; the
# scripts those blocks invoke were never linted by anything.
lint_scripts() {
  section "Shell scripts (shellcheck)"

  if ! have shellcheck; then
    printf '%sSKIP%s shellcheck not found — run: mise install\n' "$yellow" "$reset"
    FAILED+=("shell-scripts (tool missing)")
    return
  fi

  if shellcheck scripts/*.sh; then
    printf '%sOK%s shell scripts\n' "$green" "$reset"
  else
    printf '%sFAIL%s shell scripts\n' "$red" "$reset"
    FAILED+=("shell-scripts")
  fi
}

lint_workflows
lint_scripts
audit_workflows

section "Summary"
if [[ ${#FAILED[@]} -eq 0 ]]; then
  printf '%sAll GitHub Actions checks passed.%s\n' "$green" "$reset"
  exit 0
fi
printf '%sFailed checks:%s %s\n' "$red" "$reset" "${FAILED[*]}"
exit 1
