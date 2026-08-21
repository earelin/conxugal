---
feat: FEAT-0015
domain: backend
adrs: []
status: todo
depends_on: []
---

# Rename the import executor to a family-neutral name

`contratos-menores-import` is about to be the qualifier **both** contract families inject, and the
constant that publishes it lives on `StartContratosMenoresImport`. Renaming the value while leaving
it hosted by a family-named class would be the same rot one level up, so this task moves both.

Housekeeping, independent of everything else in this feature, and **its own task on purpose**. It
was a clause inside [TASK-0017](TASK-0017-the-licitacions-triggers.md) in an earlier draft, which
was wrong twice: it reaches outside this feature, and bundling it would have hidden that inside a
task about two endpoints.

**What it reaches:**

| Where | What |
| --- | --- |
| `StartContratosMenoresImport:42,55` | the `IMPORT_EXECUTOR` constant and its `@Named` injection site |
| `application/src/main/resources/application.yml:27` | the `micronaut.executors.contratos-menores-import` key |
| `ContratosMenoresImportExecutorIntegrationTest:27` | asserts the executor exists under that name — **FEAT-0009 TASK-0010's own acceptance criterion** |
| `FEAT-0014/TASK-0006-the-scheduler.md:52,53,60` | a `status: todo` task that names the string as a **prohibition** |
| `FEAT-0014/README.md:316,317` | the same, in prose |

The FEAT-0014 rows are why this is not a two-line change. That task says the scheduler *"must not
reuse `contratos-menores-import`"*; after the rename its text names a string that no longer exists,
and nobody working on FEAT-0014 has any reason to look. The rename either carries those two
documents with it or it silently invalidates a sibling feature's instructions.

It fails loudly rather than silently if botched — the integration test exists to catch exactly a
name mismatch — which is what makes it safe to do at all.

## Scope

- **A family-neutral name for the executor**: `contract-import`, and the config key with it.
- **The constant moves off `StartContratosMenoresImport`** to a home neither family owns, so
  `StartLicitacionsImport` and `StartMarkedOrganoImport` do not import their qualifier from the
  contratos menores use case. A small `ImportExecutor` holder in the same package is enough; no
  interface, no factory.
- **`application.yml`** — the key and the comment above it, which also names the family.
- **`ContratosMenoresImportExecutorIntegrationTest`** — the asserted name, and its own name if it is
  no longer about one family.
- **FEAT-0014's `TASK-0006` and `README.md`** — the four references, so the prohibition still names
  a real string and still means what it meant.

**Out of scope:** any change to what the executor *is* — it stays the same `fixed`, virtual-threaded
pool with the same sizing, because the guard admits one import at a time and nothing about that has
changed. No second executor.

## Acceptance criteria

- No source file, config key or test in the repository refers to `contratos-menores-import`; a grep
  over `server/`, `ui/` and `docs/` finds nothing.
- The executor exists under its new name at runtime, asserted by the moved integration test, and a
  contratos menores import still runs on it end to end.
- The `@Named` qualifier and the `micronaut.executors` key agree — a mismatch is what this rename
  most risks, and it is exactly what the integration test proves.
- FEAT-0014's `TASK-0006` and `README.md` name the new string, and their prohibition still reads as
  a prohibition.
- `scripts/docs-lint.sh` passes.
