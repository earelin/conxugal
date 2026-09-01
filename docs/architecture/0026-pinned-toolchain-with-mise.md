---
status: accepted
date: 2026-09-01
spec: null
supersedes: null
superseded_by: null
---

# 0026. Pin the developer toolchain in `.tool-versions`, installed by mise

## Status
Accepted

## Context
Tool versions were written in six unrelated places, and three of them were not versions at
all:

- `ui/package.json`'s `volta.node` held Node 24.18.0 and was read by four workflows through
  `node-version-file`, while `docs-lint.yml` asked `setup-node` for a literal `"24"` — the two
  had already drifted.
- `server-ci.yml` (twice) and `contract-fuzz.yml` each carried `java-version: "25"`.
- `actions-lint.yml` pinned `ZIZMOR_VERSION: "1.28.0"` for CI only; a contributor got whatever
  `brew install zizmor` produced that day.
- `actionlint` came from the upstream installer invoked with `latest`, `lychee` from a
  `releases/latest` tarball, and `vacuum` from `curl -fsSL https://quobix.com/... | sh`. None
  of the three was pinned in CI at all.

Local setup was a page of `brew install` and `npm i -g` in `README.md`, with a paragraph
telling non-macOS contributors to improvise. Two consequences followed. A quality gate could
pass locally and fail in CI, or the reverse, purely because the two ran different linters. And
an unrelated pull request could go red overnight when an unpinned linter shipped a new rule.

The Java claim was worse than unpinned: `README.md` and `server/README.md` both stated the
toolchain was "auto-provisioned by Gradle if not installed". `server/settings.gradle.kts`
applies no toolchain resolver plugin, and Gradle cannot auto-provision without one, so a
contributor without a JDK 25 already got `No matching toolchains found`.

## Decision
Pin every developer-facing tool once, in a root **`.tool-versions`** installed by
**[mise](https://mise.jdx.dev)**.

The asdf format is the one thing all three consumers understand: mise locally,
`actions/setup-java` and `actions/setup-node` in CI. That keeps a single copy of each version
rather than a pin plus a matching literal somewhere else. `scripts/setup-dev-env.sh` runs
`mise install`, installs the UI dependencies with the pinned Node, and checks that each tool
on the `PATH` really is mise's copy.

**`.tool-versions` is the source of truth.** A `mise.toml` may be added only for a tool the
asdf format cannot express — one needing a `url`, a `bin_path` or a `checksum` — and may then
contain `[tools]` and nothing else. The criterion is the format, not the tool. This is not
tidiness: `mise trust` grants by path rather than by content, and a config of plain tool
versions is exempt from it altogether. Restricting the file keeps every clone trust-free and
turns a hunk adding `[env]` or `[tasks]` into a security review rather than a config tweak.
Nothing in this repository needs such an entry today, so no `mise.toml` exists.

**CI splits by what the job costs.** The JDK- and Node-heavy workflows (`server-ci`,
`contract-fuzz`, `ui-ci`, `ui-acceptance`) keep `setup-java` and `setup-node`, repointed at
`.tool-versions`: the runners carry Node in their tool cache and `setup-java` restores Temurin
from it, whereas mise would download a full JDK in every job and claim its own cache entry. The
three lint workflows use `jdx/mise-action` with `install_args` naming only their own tools.
They need no JDK, the tools involved total tens of megabytes, and the alternative was the three
unpinned installers above. `.tool-versions` is listed in every workflow's `paths:` filter, so a
bump triggers the builds that consume it.

Volta is removed. `ui/package.json` keeps `engines.node` as a range, not a pin: it states what
the package requires, while the version it is built with is stated in one place.
`server/gradle.properties` gains `org.gradle.java.installations.fromEnv=JAVA_HOME` — Gradle's
toolchain detection covers SDKMAN, asdf and jabba but has no mise detector, and this adds the
JDK mise exports to the candidate list without naming an absolute path.

`JavaLanguageVersion.of(25)` in the Gradle conventions stays where it is. That is the language
level the code compiles to; `.tool-versions` says which JDK build provides it. Two facts, two
places, correctly.

Schemathesis is untouched. [ADR-0021](0021-openapi-contract-testing-with-schemathesis.md) runs
it from a pinned Docker image precisely so that no Python toolchain enters contributor setup,
and that reasoning is unaffected by mise being available.

## Consequences
- One command replaces a page of setup instructions, and it works the same on every operating
  system — the "outside macOS, improvise" paragraph is gone, along with the piped remote
  installer it recommended.
- Local quality gates now run the same binaries as CI, so a green run locally means something
  it did not mean before.
- Java stops depending on an auto-provisioning this build never had. The false claim in both
  READMEs is corrected rather than papered over.
- **Dependabot cannot bump `.tool-versions`** — it reads manifests for the ecosystems it knows,
  and there is no asdf ecosystem. Nine pins therefore become a manual obligation, the same trap
  ADR-0021 records for the Schemathesis image tag. Renovate's `mise`/`asdf` managers would
  automate it, and adopting Renovate for this file is the obvious escalation if the pins go
  stale.
- **This reverses a decision recorded in `docs-lint.yml`**, which deliberately left
  `markdownlint-cli2` and `@probelabs/maid` unlocked on the grounds that pinning throwaway
  linters buys a bump obligation Dependabot cannot see. That reasoning still holds on its own
  terms; it loses to keeping every gate reproducible and to having one place to look. `maid` at
  `0.0.29` is pre-1.0 and moving, so expect it to be the first pin to rot.
- Pinning `shellcheck` and `zizmor` means a new upstream audit no longer arrives unannounced in
  an unrelated pull request. It arrives when someone bumps the file — which is the point, and
  is also a chore this repository now owns. Adopting the pins already carried zizmor from
  1.28.0 to 1.30.0.
- `jdx/mise-action` is a new third-party action in the supply chain. It is SHA-pinned like
  every other action here, and the existing `github-actions` Dependabot entry covers it.
- `java temurin-25.0.4` is a prefix match, not an exact build: mise resolves it to the newest
  matching Adoptium build, currently `temurin-25.0.4+101.0.LTS`. The patch level is pinned, the
  build number floats — deliberately, because mise's build suffix is its own encoding and is
  not a string `setup-java` can resolve. `setup-java` may still miss the runner tool cache and
  pay for a JDK download in three jobs; loosening the entry to `java temurin-25` is the escape
  hatch if that cost shows up.
- **mise itself is pinned too**, with the action's `version:` input. Left unset, `mise-action`
  installs whatever released that day, and the cache that would otherwise mask the drift is
  keyed on a hash of `.tool-versions` — so the resolver, the bundled registry snapshot and
  every backend default would change on precisely the commits that bump a tool version. That
  pin is a tenth manual bump obligation, and the reason it is worth one.
- mise itself is now a prerequisite that nothing else can install, which is why
  `setup-dev-env.sh` checks for it first and stops with the install command rather than trying
  to proceed.
- The `PATH` check is a real gate, not a formality: a contributor with an older
  `brew install actionlint` gets a `shadowed` failure telling them to remove it, and every
  existing contributor has Volta — the previous Node pin was `ui/package.json`'s
  `volta.node` — so this fires on the first run for almost everyone. It compares against the
  exact path `mise which` reports (accepting the shims directory) rather than testing mise's
  data directory as a prefix, because an `npm i -g` run with mise's own node installs into
  `installs/node/<version>/bin`, which a prefix test would wave through.
