#!/usr/bin/env bash
#
# setup-dev-env.sh — install the pinned toolchain and verify the shell resolves it.
#
#   1. mise install     — every tool in .tool-versions
#   2. PATH             — each tool resolves to mise's copy, not another one
#   3. npm ci           — ui/ dependencies, with the pinned Node
#
# All steps run even if an earlier one fails, so a single invocation reports
# everything. Exit status is non-zero if any step fails.
#
# Usage: scripts/setup-dev-env.sh
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

bold=$(tput bold 2>/dev/null || true)
red=$(tput setaf 1 2>/dev/null || true)
green=$(tput setaf 2 2>/dev/null || true)
yellow=$(tput setaf 3 2>/dev/null || true)
reset=$(tput sgr0 2>/dev/null || true)

FAILED=()
UNREACHABLE=()

section() { printf '\n%s==> %s%s\n' "$bold" "$1" "$reset"; }
have()    { command -v "$1" >/dev/null 2>&1; }

# The one dependency this script cannot resolve for you.
if ! have mise; then
  printf '%sFAIL%s mise not found. Install it, then run this script again:\n' "$red" "$reset"
  printf '  curl https://mise.run | sh        # or: brew install mise\n'
  printf 'Then activate the shell hook: https://mise.jdx.dev/getting-started.html\n'
  exit 1
fi

# --- 1. Toolchain -------------------------------------------------------------
install_tools() {
  section "Toolchain (mise install)"
  if mise install; then
    printf '%sOK%s toolchain installed\n' "$green" "$reset"
  else
    printf '%sFAIL%s mise install\n' "$red" "$reset"
    FAILED+=("toolchain")
  fi
}

# --- 2. PATH ------------------------------------------------------------------
# Compared against the exact path `mise which` reports, not against mise's data
# directory as a prefix. The difference matters: an `npm i -g` run with mise's own
# node installs into installs/node/<version>/bin, a path inside mise's data
# directory that a prefix test would accept — the very drift this step exists to
# catch.
#
# The shims directory is the one other acceptable answer: `mise activate` puts the
# install directories on the PATH, but shim mode puts this single directory there
# instead, and a shim is still the pinned copy.
SHIMS_DIR="${MISE_DATA_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/mise}/shims"

# Binary names to check on the PATH, read out of .tool-versions so the two cannot
# drift. Only the npm entries need translating: mise names them by package
# (npm:@probelabs/maid), which is not the binary they install (maid).
expected_binaries() {
  local tool _
  while read -r tool _; do
    [[ -z "$tool" || "$tool" == \#* ]] && continue
    tool="${tool#npm:}"
    printf '%s\n' "${tool##*/}"
  done < "$ROOT/.tool-versions"
}

check_path() {
  section "PATH"
  local tool actual expected resolved
  for tool in $(expected_binaries); do
    expected="$(mise which "$tool" 2>/dev/null)"
    if [[ -z "$expected" ]]; then
      printf '%sFAIL%s %-18s not installed by mise\n' "$red" "$reset" "$tool"
      FAILED+=("path: $tool")
      continue
    fi

    actual="$(command -v "$tool" 2>/dev/null)"
    if [[ -z "$actual" ]]; then
      # A first `mise install` never changes the PATH of the shell that launched
      # it, so this is expected on a fresh machine rather than broken. Counted, not
      # failed: the summary has to say the toolchain is unreachable rather than
      # report success over a PATH it never actually verified.
      printf '%sWARN%s %-18s unreachable\n' "$yellow" "$reset" "$tool"
      UNREACHABLE+=("$tool")
      continue
    fi

    resolved="$(readlink -f "$actual" 2>/dev/null || printf '%s' "$actual")"
    if [[ "$actual" == "$expected" || "$resolved" == "$expected" || "$actual" == "$SHIMS_DIR/$tool" ]]; then
      printf '%sOK%s   %-18s %s\n' "$green" "$reset" "$tool" "$actual"
    else
      printf '%sFAIL%s %-18s shadowed by %s\n' "$red" "$reset" "$tool" "$actual"
      printf '       remove that copy so the pinned one wins (expected %s)\n' "$expected"
      FAILED+=("path: $tool")
    fi
  done
}

# --- 3. UI dependencies -------------------------------------------------------
# Through `mise exec` on purpose: the npm on the PATH may be another one, or
# missing entirely on a fresh machine, and installing dependencies with a Node
# other than the pinned one is exactly the drift this setup avoids.
install_ui_dependencies() {
  section "UI dependencies (npm ci)"
  if (cd "$ROOT/ui" && mise exec -- npm ci); then
    printf '%sOK%s ui/node_modules\n' "$green" "$reset"
  else
    printf '%sFAIL%s npm ci\n' "$red" "$reset"
    FAILED+=("npm ci")
  fi
}

install_tools
check_path
install_ui_dependencies

section "Summary"

# Docker is a prerequisite mise does not manage: Testcontainers, docker compose
# and the pinned Schemathesis image all need a running daemon.
if ! have docker; then
  printf '%sWARN%s docker not found — integration, acceptance and contract tests need it\n' "$yellow" "$reset"
fi

if [[ ${#FAILED[@]} -gt 0 ]]; then
  printf '%sFailed steps:%s %s\n' "$red" "$reset" "${FAILED[*]}"
  exit 1
fi

# Not a pass: the toolchain is installed but this shell resolves none of it, so
# the PATH check verified nothing. Saying "ready" here would send the contributor
# straight into `./gradlew build` and "No matching toolchains found".
if [[ ${#UNREACHABLE[@]} -gt 0 ]]; then
  printf '%sToolchain installed, but this shell resolves none of it.%s\n' "$yellow" "$reset"
  printf 'Add this to your shell rc, then run this script again to verify the PATH:\n'
  # shellcheck disable=SC2016  # the command is meant to be shown, not expanded
  printf '  eval "$(mise activate %s)"\n' "$(basename "${SHELL:-bash}")"
  exit 1
fi

printf '%sDevelopment environment ready.%s\n' "$green" "$reset"
exit 0
