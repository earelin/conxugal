# Issue authoring

An issue is a **PR-sized slice** of a feature — the smallest traceable unit of work.
Issues live on the filesystem, **not** on GitHub.

## Layout

Issues are grouped in one folder per feature:

```
docs/issues/
  FEAT-NNN/                    # one folder per feature, named exactly after the FEAT
    ISSUE-NNN-kebab-title.md   # NNN is sequential within the feature folder
```

## Issue doc format

- Filename: `ISSUE-NNN-kebab-title.md`. `NNN` restarts at `001` in each `FEAT-NNN/` folder.
- Frontmatter:
  ```yaml
  ---
  feat: FEAT-NNN          # parent feature (required)
  adrs: [NNNN]            # governing ADRs, if any
  status: todo            # todo | in-progress | done
  depends_on: []          # other issues in this feature that must land first, e.g. [ISSUE-001]
  ---
  ```
- Body: a short goal, a **Scope** list of what the PR touches, and **testable acceptance
  criteria** tracing back to the spec (e.g. `SPEC-001 #4`) or feature requirements.

## Rules

- One issue ≈ one PR. Keep scope to the single issue.
- Every issue must trace up: `feat:` frontmatter to its parent feature, which traces to a
  spec. If the feature or spec is missing, **STOP and propose it first** — do not write
  implementation code against an untraced issue.
- Record cross-issue ordering in `depends_on:` rather than prose.
- Flip `status:` as work moves: `todo → in-progress → done`.
