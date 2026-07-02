# Task authoring

A task is a **small, self-contained slice** of a feature — the smallest traceable unit
of work. Tasks live on the filesystem, **not** on GitHub.

## Layout

Tasks are grouped in one folder per feature:

```
docs/tasks/
  FEAT-NNN/                   # one folder per feature, named exactly after the FEAT
    TASK-NNN-kebab-title.md   # NNN is sequential within the feature folder
```

## Task doc format

- Filename: `TASK-NNN-kebab-title.md`. `NNN` restarts at `001` in each `FEAT-NNN/` folder.
- Frontmatter:
  ```yaml
  ---
  feat: FEAT-NNN          # parent feature (required)
  adrs: [NNNN]            # governing ADRs, if any
  status: todo            # todo | in-progress | done
  depends_on: []          # other tasks in this feature that must land first, e.g. [TASK-001]
  ---
  ```
- Body: a short goal, a **Scope** list of what the change touches, and **testable acceptance
  criteria** tracing back to the spec (e.g. `SPEC-001 #4`) or feature requirements.

## Rules

- Keep each task to a small, self-contained change committed straight to `trunk`
  (trunk-based development — no long-lived branches or pull requests).
- Every task must trace up: `feat:` frontmatter to its parent feature, which traces to a
  spec. If the feature or spec is missing, **STOP and propose it first** — do not write
  implementation code against an untraced task.
- Record cross-task ordering in `depends_on:` rather than prose.
- Flip `status:` as work moves: `todo → in-progress → done`.
