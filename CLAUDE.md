# CLAUDE.md

## Project overview

Aplicación web para extraer, almacenar, analizar e exportar a información de contratos públicos da Xunta de Galicia desde contratosdegalicia.gal.

## Spec-driven workflow

Work flows **spec → feature → GitHub issue**, with **architecture decisions (ADRs)**
recorded orthogonally. Do not write implementation code without a traced issue that
links up to a feature and a spec.

```
docs/
  specs/        SPEC-NNN-*.md   # the "what": requirements + acceptance criteria, impl-agnostic
  features/     FEAT-NNN-*.md   # a buildable slice of a spec; design lives here; maps to a GitHub parent issue
  architecture/ NNNN-*.md       # ADRs: one architecturally significant decision each
GitHub Issues                 # PR-sized work
```

The trace spans two systems: `SPEC → FEAT` via frontmatter on the filesystem, and
`FEAT → parent issue → sub-issues` via native GitHub parent/child links.

### Before coding — check the chain

1. Confirm the `SPEC → FEAT → issue` chain exists for the work.
2. If a level is missing, **propose the missing doc(s) and STOP for review** before implementing.
3. When implementing an issue, first read its parent feature, its spec, and any referenced ADRs.
4. Keep scope to the single issue. One sub-issue ≈ one PR.

