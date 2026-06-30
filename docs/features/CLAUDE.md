# Feature authoring + GitHub issues

A feature is a **buildable slice** of a spec.
Design decisions live here, and a feature maps to a **list of small, PR-sized
GitHub issues** (no parent issue).

## Feature doc format

- Filename: `FEAT-NNN-kebab-title.md` (sequential `NNN`).
- Frontmatter:
  ```yaml
  ---
  spec: SPEC-NNN          # parent spec (required)
  adrs: [NNNN]            # governing ADRs, if any
  status: draft           # draft | active | implemented
  ---
  ```
- Body: the design — components, contracts, sequencing, edge cases. This is the place for
  implementation detail that specs deliberately omit.

## GitHub issues

Issues live on GitHub, not the filesystem. A feature gets **no parent issue** — it
maps to a **flat list of small, PR-sized issues**, one issue ≈ one PR.

- Group a feature's issues with a **per-feature label** `FEAT-NNN` (create it once).
  This is a personal (non-org) repo, so native **custom issue types are unavailable** —
  use labels, never `gh issue create --type`.
- Each issue body MUST reference the `FEAT-NNN` it implements and any governing
  `ADR-NNNN`, so the trace survives without a parent link.
- Record every created issue back into the feature doc's **`## GitHub issues`**
  section (number + title) so the feature lists its full work breakdown.

```bash
# one-time: a label to group the feature's issues
gh label create FEAT-002 --description "FEAT-002 login process" --color 1D76DB

# a PR-sized issue, linked back to the feature + governing ADR
gh issue create --label FEAT-002 \
  --title "Domain: User, Role and authenticate use case" \
  --body $'Implements FEAT-002. Governed by ADR-0004.\n\nAcceptance criteria:\n- ...'

# read the feature's issues back
gh issue list --label FEAT-002 --state all
```

Use `--blocked-by` / `--blocking` to record dependencies between issues when relevant.
