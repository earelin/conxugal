# Spec authoring

A spec describes a capability at the **what** level, independent of how it's built.

## Format

- Filename: `SPEC-NNNN-kebab-title.md` (sequential `NNNN`).
- Frontmatter:
  ```yaml
  ---
  status: draft        # draft | active | superseded
  ---
  ```
- Body: the capability, its requirements, and **testable acceptance criteria**.

## Rules

- No design or implementation detail — no class names, schemas, libraries, or sequencing.
  Those belong in the feature doc.
- Acceptance criteria must be verifiable (a reader can decide pass/fail without guessing).
- Specs change rarely. If a requirement shifts materially, prefer a new spec over rewriting,
  and mark the old one `superseded`.
- One spec can spawn many features; keep the spec broad and stable.
