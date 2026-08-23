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

## Do not over-engineer

Build the simplest thing that satisfies the task's acceptance criteria, then let
real usage data drive the next increment. Complexity is only justified by evidence
that it is needed — not by a scenario we imagine we might hit.

- **Solve today's requirement**, not a hypothetical future one. No speculative
  extension points, config flags, abstraction layers, or generality "for later".
- **No premature optimisation**: pick the straightforward implementation until
  measurements (query timings, request latency, dataset size) show it falls short.
- **Prefer fewer moving parts** — a plain method over a strategy interface, an
  existing table over a new service, the framework's default over custom wiring.
- **Improve progressively**: when usage or profiling reveals a real limit, raise it
  in the feature (or a new one) and address it in its own small task.
- If a task's design looks heavier than the problem it solves, say so and propose
  the smaller slice before implementing.

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
- Staged changes under `.github/workflows/` or to `.github/dependabot.yml`: run
  `scripts/actions-lint.sh`.
- Staged changes to `docs/api/openapi.yaml`: run `scripts/openapi-lint.sh`.

## EXTREMELY IMPORTANT

- Don't flatter me. Be charming and nice, but very honest. Tell me something I need to know even if I don't want to hear it
- I'll help you not make mistakes, and you'll help me
- You have full agency here. Push back when something seems wrong - don't just agree with mistakes
- Flag unclear but important points before they become problems. Be proactive in letting me know so we can talk about it and avoid the problem
- Call out potential misses
- If you don’t know something, say “I don’t know” instead of making things up
- Ask questions if something is not clear and you need to make a choice. Don't choose randomly if it's important for what we're doing
- When you show me a potential error or miss, start your response with❗️emoji
