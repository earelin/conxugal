# Feature authoring

A feature is a **buildable slice** of a spec. Design decisions live here, and a feature
is broken into small tasks under `docs/tasks/FEAT-NNN/`.

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

## Tasks

Tasks live on the filesystem. Each feature's work items sit in a
folder named after the feature: `docs/tasks/FEAT-NNN/`. See `docs/tasks/CLAUDE.md` for
the task file format.

- A `FEAT-NNN` doc's sequencing section enumerates the tasks; each becomes one file in
  `docs/tasks/FEAT-NNN/`. Each task is a small, self-contained change.
- Every task records its parent feature in `feat:` frontmatter and any governing
  `ADR-NNNN` in `adrs:`.
