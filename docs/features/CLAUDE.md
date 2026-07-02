# Feature authoring

A feature is a **buildable slice** of a spec. Design decisions live here, and a feature
is broken into small issues under `docs/issues/FEAT-NNN/`.

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

## Issues

Issues live on the filesystem. Each feature's work items sit in a
folder named after the feature: `docs/issues/FEAT-NNN/`. See `docs/issues/CLAUDE.md` for
the issue file format.

- A `FEAT-NNN` doc's sequencing section enumerates the issues; each becomes one file in
  `docs/issues/FEAT-NNN/`. Each issue is a small, self-contained change.
- Every issue records its parent feature in `feat:` frontmatter and any governing
  `ADR-NNNN` in `adrs:`.
