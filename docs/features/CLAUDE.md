# Feature authoring + GitHub issues

A feature is a **buildable slice** of a spec.
Design decisions live here, and a feature maps to one GitHub parent issue.

## Feature doc format

- Filename: `FEAT-NNN-kebab-title.md` (sequential `NNN`).
- Frontmatter:
  ```yaml
  ---
  spec: SPEC-NNN          # parent spec (required)
  github_issue: <n>       # the GitHub parent issue, recorded once created
  adrs: [NNNN]            # governing ADRs, if any
  status: draft           # draft | active | implemented
  ---
  ```
- Body: the design — components, contracts, sequencing, edge cases. This is the place for
  implementation detail that specs deliberately omit.

## GitHub issues

Issues live on GitHub, not the filesystem. Requires `gh >= 2.94.0` (native sub-issues,
issue types, dependencies).

- A `FEAT-NNN` doc maps to one **parent issue** (type: `Feature`). Record its number back
  into the feature frontmatter as `github_issue`.
- PR-sized work is a **sub-issue** of that parent. Its body MUST reference the `FEAT-NNN`
  it implements and any governing `ADR-NNNN`. One sub-issue ≈ one PR.

```bash
# feature → parent issue (once); capture the returned number into frontmatter
gh issue create --type Feature --title "FEAT-001 Officer extraction" \
  --body-file docs/features/FEAT-001-officer-extraction.md

# work item → sub-issue of the feature's parent
gh issue create --parent 42 --type Task \
  --title "Grammar/regex parser for officer block" \
  --body $'Implements FEAT-001. Governed by ADR-0001.\n\nAcceptance criteria:\n- ...'

# read the trace back as JSON
gh issue view 55 --json number,title,parent,type,body
```

Use `--blocked-by` / `--blocking` to record dependencies between sub-issues when relevant.
