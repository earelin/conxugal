# Feature & task authoring

A feature is a **buildable slice** of a spec. Design decisions live here, and a feature is
broken into small tasks. **Each feature is a folder** under `docs/features/`; its `README.md`
holds the description, and its tasks live alongside it in that same folder.

## Layout

```
docs/features/
  FEAT-NNNN-kebab-title/         # one folder per feature (sequential NNNN)
    README.md                    # the feature description
    TASK-NNNN-kebab-title.md     # tasks; NNNN restarts at 0001 within each feature
```

## Feature doc format

- Folder: `FEAT-NNNN-kebab-title/`; the description is its `README.md`.
- Frontmatter:
  ```yaml
  ---
  spec: SPEC-NNNN          # parent spec (required)
  adrs: [NNNN]            # governing ADRs, if any
  status: draft           # draft | active | implemented
  ---
  ```
- Body: the design — components, contracts, sequencing, edge cases. This is the place for
  implementation detail that specs deliberately omit.
- A feature's sequencing section enumerates the tasks; each becomes one `TASK-NNNN` file in the
  same folder. Each task is a small, self-contained change.

## Task doc format

A task is the **smallest traceable unit of work** — a small, self-contained change. Tasks live
on the filesystem, **not** on GitHub.

- Filename: `TASK-NNNN-kebab-title.md` inside the feature folder. `NNNN` restarts at `0001` in
  each feature folder.
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
- Every task and feature must trace up: a task's `feat:` frontmatter to its parent feature,
  which traces to a spec via `spec:`. If the feature or spec is missing, **STOP and propose it
  first** — do not write implementation code against an untraced task.
- Record cross-task ordering in `depends_on:` rather than prose.
- Flip a task's `status:` as work moves: `todo → in-progress → done`.
