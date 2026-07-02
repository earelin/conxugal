# CLAUDE.md

## Project overview

Aplicación web para extraer, almacenar, analizar e exportar a información de contratos públicos da Xunta de Galicia desde contratosdegalicia.gal.

## Spec-driven workflow

Work flows **spec → feature → issue**, with **architecture decisions (ADRs)**
recorded orthogonally. Do not write implementation code without a traced issue that
links up to a feature and a spec.

```
docs/
  specs/        SPEC-NNN-*.md          # the "what": requirements + acceptance criteria, impl-agnostic
  features/     FEAT-NNN-*.md          # a buildable slice of a spec; design lives here
  issues/       FEAT-NNN/ISSUE-NNN-*.md # small, self-contained work, one folder per feature
  architecture/ NNNN-*.md              # ADRs: one architecturally significant decision each
```

The trace is entirely on the filesystem: `SPEC → FEAT` via feature frontmatter, and
`FEAT → ISSUE` via the `docs/issues/FEAT-NNN/` folder plus each issue's `feat:` frontmatter.

### Before coding — check the chain

1. Confirm the `SPEC → FEAT → ISSUE` chain exists for the work.
2. If a level is missing, **propose the missing doc(s) and STOP for review** before implementing.
3. When implementing an issue, first read its parent feature, its spec, and any referenced ADRs.
4. Keep scope to the single issue: a small, self-contained change committed straight to
   `trunk` (trunk-based development — no long-lived branches or pull requests).
