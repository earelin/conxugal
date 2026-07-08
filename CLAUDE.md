# CLAUDE.md

## Project overview

Aplicación web para extraer, almacenar, analizar e exportar a información de contratos públicos da Xunta de Galicia desde contratosdegalicia.gal.

## Spec-driven workflow

Work flows **spec → feature → task**, with **architecture decisions (ADRs)**
recorded orthogonally. Do not write implementation code without a traced task that
links up to a feature and a spec.

```
docs/
  specs/        SPEC-NNNN-kebab.md                   # the "what": requirements + acceptance criteria, impl-agnostic
  features/     FEAT-NNNN-kebab/README.md            # a buildable slice of a spec; design lives here
                FEAT-NNNN-kebab/TASK-NNNN-kebab.md   # small, self-contained work, alongside its feature
  architecture/ NNNN-kebab.md                        # ADRs: one architecturally significant decision each
```

Each level narrows the one above: a **spec** stays at the *what* level (no design
detail); a **feature** is a buildable slice where design decisions live and are
enumerated as tasks; a **task** is the smallest self-contained change. The trace is
entirely on the filesystem — `SPEC → FEAT` via the feature's `spec:` frontmatter,
and `FEAT → TASK` via the task living inside the `docs/features/FEAT-NNNN-*/` folder
plus its `feat:` frontmatter. Governing decisions are cited in `adrs:`.

### Before coding — check the chain

1. Confirm the `SPEC → FEAT → TASK` chain exists for the work.
2. If a level is missing, **propose the missing doc(s) (via the matching `create-*`
   skill) and STOP for review** before implementing. Likewise, if a task implies an
   architecturally significant decision with no ADR, propose the ADR first.
3. When implementing a task, first read its parent feature, its spec, and any ADRs it
   cites; honour `depends_on:` ordering and flip `status:` as work moves.
4. Keep scope to the single task: a small, self-contained change.

## Code style

Do not add unnecessary comments. Skip anything the code already states plainly;
comment only the non-obvious — intent, trade-offs, or reasons that aren't visible
from the code itself.

- **Never comment build scripts** (Gradle `build.gradle.kts`, version catalogs, npm
  configs, etc.) — dependency declarations and build wiring should read for
  themselves; put rationale in the commit message instead.
- **Never comment trivial SQL** (e.g. a `CREATE TABLE` restating its own column
  names/types) — only comment a genuinely non-obvious constraint.
- **Never reference spec/feature/task/ADR identifiers** (`SPEC-NNNN`, `FEAT-NNNN`,
  `TASK-NNNN`, `ADR-NNNN`) in code comments — that traceability lives in the commit
  message and the `docs/` tree, not in source comments that rot as work moves on.

## Before committing

Run the relevant lint script and fix any failures before committing — CI
(`.github/workflows/`) re-checks these on push, but catch failures locally first:

- Staged changes under `docs/` (or root `*.md`/`CLAUDE.md` files): run `scripts/docs-lint.sh`.
- Staged changes under `.github/workflows/`: run `scripts/actions-lint.sh`.
