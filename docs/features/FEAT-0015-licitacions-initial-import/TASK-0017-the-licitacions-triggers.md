---
feat: FEAT-0015
domain: backend
adrs: [0005, 0006, 0010, 0012, 0020, 0021]
status: todo
depends_on: [TASK-0016]
---

# The licitacións triggers

Two `ADMIN`-only endpoints, and the use case that hands the long half to an executor. The
administrator's way to load a family that, until the incremental feature lands, has no other way to
be loaded.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/admin/licitacions/import` | Trigger over every marked, active Órgano (R27) |
| `POST` | `/api/admin/organo/{id}/licitacions/import` | Trigger over one Órgano (R27) |

Authored in `openapi.yaml` **first**
([ADR-0010](../../architecture/0010-design-first-openapi-contract.md)), named per
[ADR-0020](../../architecture/0020-actions-as-verbs-in-rest-paths.md), under the reserved prefix of
[ADR-0006](../../architecture/0006-reserved-api-url-prefix.md), gated by session security
([ADR-0005](../../architecture/0005-session-based-authentication.md)) and carrying
[ADR-0012](../../architecture/0012-rate-limit-http-contract.md)'s rate-limit contract. Conformance
under [ADR-0021](../../architecture/0021-openapi-contract-testing-with-schemathesis.md).

## Scope

- **`openapi.yaml` first**: both paths, `202` with a run identifier, and the **two distinct
  refusals** as separate problem types — the guard's `import-already-running` and
  `organo-not-eligible`, both already published and both reused rather than duplicated. R27 requires
  the two reasons be told apart, so one `409` covering both would not satisfy it.
- **`StartLicitacionsImport`**, on `StartContratosMenoresImport`'s shape: claim synchronously, hand
  the walking to the executor, answer the run identity. Claiming answers in milliseconds and walking
  runs for hours, and they stay two use cases so neither imposes on the other.
- **The same executor**, injected by the name `StartContratosMenoresImport` already publishes,
  because the guard admits one import at a time and a second executor would only add a way to forget
  that. **Renaming that qualifier is [TASK-0024](TASK-0024-rename-the-import-executor.md)**, not
  this task: the string reaches `application.yml`, a shipped integration test that is FEAT-0009's
  own acceptance criterion, and a `todo` FEAT-0014 task that names it as a prohibition. Bundling it
  here would rewrite a `done` feature's proof and silently invalidate a sibling's text.
- **A submission the executor refuses settles the run `FAILED` before it propagates**, exactly as
  the shipped path does: the run is claimed by then, and left standing it would hold the system-wide
  guard until the abandonment bound passed, refusing every import in the meantime for work no thread
  was ever going to do.
- **`LicitacionsImportController`** — the two endpoints, `@Secured("ADMIN")`, refusals left to the
  existing handlers. Nothing is caught here.

**Out of scope:** the mark endpoint's change to request both families
([TASK-0018](TASK-0018-start-marked-organo-import.md)), the executor rename (TASK-0024), any read of
a licitación, the scheduler, and R29's yielding — so **#40's yield clauses are not claimed by this
task**, only its refusal half.

## Acceptance criteria

- `POST /api/admin/licitacions/import` answers **`202`** with a run identifier and returns before the
  walk has finished. ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #1 trigger
  half, #38 trigger half)
- `POST /api/admin/organo/{id}/licitacions/import` answers `202` for a marked, active Órgano.
  (SPEC-0008 #1 trigger half)
- With a run live, both triggers refuse with the **guard's** problem type; against an unmarked or
  inactive Órgano, the single-Órgano trigger refuses with the **ineligibility** problem type. The two
  are distinguishable by type, not by status code alone. (SPEC-0008 #40 refusal half)
- A refused trigger **writes no run row** — verified against the store, since a claimed-and-refused
  run would hold the guard for the abandonment bound. (SPEC-0008 #40 refusal half)
- A non-`ADMIN` authenticated user is refused, and an unauthenticated request is refused, on both
  paths.
- An executor that refuses the submission leaves the run settled `FAILED` rather than in progress,
  so the guard is released immediately.
- The ADR-0021 conformance run passes against the amended `openapi.yaml`, and
  `scripts/openapi-lint.sh` passes.
- Integration-tested against a running application with the source stubbed.
