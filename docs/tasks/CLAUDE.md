# Task authoring

A task is a **small, self-contained slice** of a feature — the smallest traceable unit
of work. Tasks live on the filesystem, **not** on GitHub.

## Layout

Tasks are grouped in one folder per feature:

```
docs/tasks/
  FEAT-NNNN/                   # one folder per feature, named exactly after the FEAT
    TASK-NNNN-kebab-title.md   # NNNN is sequential within the feature folder
```

## Task doc format

- Filename: `TASK-NNNN-kebab-title.md`. `NNNN` restarts at `0001` in each `FEAT-NNNN/` folder.
- Frontmatter:
  ```yaml
  ---
  feat: FEAT-NNNN          # parent feature (required)
  adrs: [NNNN]            # governing ADRs, if any
  status: todo            # todo | in-progress | done
  depends_on: []          # other tasks in this feature that must land first, e.g. [TASK-0001]
  ---
  ```
- Body: a short goal, a **Scope** list of what the change touches, and **testable acceptance
  criteria** tracing back to the spec (e.g. `SPEC-0001 #4`) or feature requirements.

## Rules

- Keep each task to a small, self-contained change committed straight to `trunk`
  (trunk-based development — no long-lived branches or pull requests).
- Every task must trace up: `feat:` frontmatter to its parent feature, which traces to a
  spec. If the feature or spec is missing, **STOP and propose it first** — do not write
  implementation code against an untraced task.
- Record cross-task ordering in `depends_on:` rather than prose.
- Flip `status:` as work moves: `todo → in-progress → done`.
