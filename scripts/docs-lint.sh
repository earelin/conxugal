#!/usr/bin/env bash
#
# docs-lint.sh — verify the Markdown documentation under docs/ (plus the
# repo-root README.md and CLAUDE.md files) with three checks:
#
#   1. Markdown format   — markdownlint-cli2 against .markdownlint.jsonc
#   2. Internal links    — lychee in offline mode (relative paths + #anchors)
#   3. Mermaid diagrams  — maid (@probelabs/maid) validates every ```mermaid block
#
# All three checks run even if an earlier one fails, so a single invocation
# reports everything. Exit status is non-zero if any check fails.
#
# Usage: scripts/docs-lint.sh
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Markdown to lint: everything under docs/ plus root-level docs. Kept explicit so
# ui/ and server/ (and their node_modules/build output) are never scanned.
MD_GLOBS=("*.md" "docs/**/*.md")

bold=$(tput bold 2>/dev/null || true)
red=$(tput setaf 1 2>/dev/null || true)
green=$(tput setaf 2 2>/dev/null || true)
yellow=$(tput setaf 3 2>/dev/null || true)
reset=$(tput sgr0 2>/dev/null || true)

FAILED=()

section() { printf '\n%s==> %s%s\n' "$bold" "$1" "$reset"; }
have()    { command -v "$1" >/dev/null 2>&1; }

# --- 1. Markdown format ------------------------------------------------------
lint_markdown() {
  section "Markdown format (markdownlint-cli2)"
  if ! have markdownlint-cli2; then
    printf '%sSKIP%s markdownlint-cli2 not found — install with: npm i -g markdownlint-cli2\n' "$yellow" "$reset"
    FAILED+=("markdown-format (tool missing)")
    return
  fi
  if markdownlint-cli2 --config "$ROOT/.markdownlint.jsonc" "${MD_GLOBS[@]}"; then
    printf '%sOK%s markdown format\n' "$green" "$reset"
  else
    printf '%sFAIL%s markdown format\n' "$red" "$reset"
    FAILED+=("markdown-format")
  fi
}

# --- 2. Internal links -------------------------------------------------------
lint_links() {
  section "Internal links (lychee --offline)"
  if ! have lychee; then
    printf '%sSKIP%s lychee not found — install from https://lychee.cli.rs (brew install lychee)\n' "$yellow" "$reset"
    FAILED+=("internal-links (tool missing)")
    return
  fi
  # --offline: never hit the network; only verify local files exist.
  # --include-fragments: also verify #anchors resolve to a heading.
  if lychee --offline --include-fragments --no-progress "${MD_GLOBS[@]}"; then
    printf '%sOK%s internal links\n' "$green" "$reset"
  else
    printf '%sFAIL%s internal links\n' "$red" "$reset"
    FAILED+=("internal-links")
  fi
}

# --- 3. Mermaid diagrams -----------------------------------------------------
# maid (@probelabs/maid) validates ```mermaid blocks in pure JS — no browser.
# It recursively scans directories/markdown and honours .gitignore.
lint_mermaid() {
  section "Mermaid diagrams (maid)"
  local -a maid
  if have maid; then
    maid=(maid)
  elif have npx; then
    maid=(npx -y @probelabs/maid)
  else
    printf '%sSKIP%s maid not found — install with: npm i -g @probelabs/maid\n' "$yellow" "$reset"
    FAILED+=("mermaid (tool missing)")
    return
  fi

  # maid exits 0 when there are no diagrams, so an empty doc set is a pass.
  if "${maid[@]}" docs *.md; then
    printf '%sOK%s mermaid diagrams\n' "$green" "$reset"
  else
    printf '%sFAIL%s mermaid diagrams\n' "$red" "$reset"
    FAILED+=("mermaid")
  fi
}

lint_markdown
lint_links
lint_mermaid

section "Summary"
if [[ ${#FAILED[@]} -eq 0 ]]; then
  printf '%sAll documentation checks passed.%s\n' "$green" "$reset"
  exit 0
fi
printf '%sFailed checks:%s %s\n' "$red" "$reset" "${FAILED[*]}"
exit 1
