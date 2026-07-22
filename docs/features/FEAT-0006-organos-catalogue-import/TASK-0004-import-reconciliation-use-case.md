---
feat: FEAT-0006
domain: backend
adrs: [0002]
status: todo
depends_on: [TASK-0001, TASK-0003]
---

# Import & reconciliation use case

`ImportOrganos`: pull the full source list and reconcile it against the stored catalogue,
atomically and idempotently, behind a single-run guard. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md) — orchestration over the
`OrganoSource` and `OrganoRepository` ports, no transport or persistence code of its own.

## Scope
- `ImportOrganos` use case: fetch the **entire** source list via `OrganoSource`, then
  reconcile against `OrganoRepository` within a **single transaction** — insert entries
  with a new source key, refresh matched entries' name/acronym **in place**, mark stored
  entries absent from the source **inactive**, and **reactivate** ones that reappear.
- A **single-run guard** owned by the use case so at most one import runs at a time; a
  trigger arriving while an import is in progress returns an "already running" result
  instead of starting a second run.
- Return an `ImportOutcome`: success/failure, counts of added / refreshed / deactivated,
  and the distinct "already running" outcome.
- Treat an unusable source result — including an empty or implausibly small list — as a
  failure that writes **nothing**, leaving the catalogue untouched.

## Acceptance criteria
- A source entry with a new key is added; an entry matching a stored key refreshes that
  row's attributes **in place**, preserving its UUID identity and any taxonomy placement.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #4, #5)
- A stored Órgano absent from the source is marked inactive and kept; when it reappears in
  a later run the **same** row is reactivated. (SPEC-0004 #6)
- Importing the same source list twice adds nothing, creates no duplicate, and changes no
  state or placement. (SPEC-0004 #7)
- On a source failure or an unusable / empty / implausibly small result, nothing is
  written, the stored catalogue and states are unchanged, and the outcome reports failure.
  (SPEC-0004 #13)
- A trigger issued while an import is already running does not start a second run; it
  returns "already running". (SPEC-0004 #12)
- A successful outcome reports the added / refreshed / deactivated counts. (SPEC-0004 #10)
- Unit-tested with fakes for `OrganoSource` and `OrganoRepository` — no database or HTTP.
