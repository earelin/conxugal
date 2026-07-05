# CLAUDE.md

## Project overview

Aplicación web para extraer, almacenar, analizar e exportar a información de contratos públicos da Xunta de Galicia desde contratosdegalicia.gal.

## Spec-driven workflow

Work flows **spec → feature → task**, with **architecture decisions (ADRs)**
recorded orthogonally. Do not write implementation code without a traced task that
links up to a feature and a spec.

```
docs/
  specs/        SPEC-NNNN-*.md                     # the "what": requirements + acceptance criteria, impl-agnostic
  features/     FEAT-NNNN-*/README.md              # a buildable slice of a spec; design lives here
                FEAT-NNNN-*/TASK-NNNN-*.md         # small, self-contained work, alongside its feature
  architecture/ NNNN-*.md                          # ADRs: one architecturally significant decision each
```

The trace is entirely on the filesystem: `SPEC → FEAT` via feature frontmatter, and
`FEAT → TASK` via the task living inside the `docs/features/FEAT-NNNN-*/` folder plus each
task's `feat:` frontmatter.

### Before coding — check the chain

1. Confirm the `SPEC → FEAT → TASK` chain exists for the work.
2. If a level is missing, **propose the missing doc(s) and STOP for review** before implementing.
3. When implementing a task, first read its parent feature, its spec, and any referenced ADRs.
4. Keep scope to the single task: a small, self-contained change committed straight to
   `trunk` (trunk-based development — no long-lived branches or pull requests).

## Before committing

Run the relevant lint script and fix any failures before committing — CI
(`.github/workflows/`) re-checks these on push, but catch failures locally first:

- Staged changes under `docs/` (or root `*.md`/`CLAUDE.md` files): run `scripts/docs-lint.sh`.
- Staged changes under `.github/workflows/`: run `scripts/actions-lint.sh`.
