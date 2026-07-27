---
feat: FEAT-0006
domain: backend
adrs: [0002, 0011]
status: done
depends_on: [TASK-0004]
---

# Scheduled import trigger

A recurring scheduler that runs the import automatically. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md) (a scheduler is a driving
entry point) and [ADR-0011](../../architecture/0011-blocking-io-virtual-threads.md) (runs
on the blocking/virtual-thread executor).

## Scope
- A Micronaut `@Scheduled` job in the `application` module that invokes the **same**
  `ImportOrganos` use case and single-run guard as the manual endpoint.
- The schedule is **configurable** (a configuration property), defaulting to **once
  daily, overnight** (a single early-morning run in the source's local time,
  Europe/Madrid) so the import lands during off-peak hours.
- Runs on the blocking/virtual-thread executor so the outbound fetch and JDBC writes block
  safely off the event loop.

## Acceptance criteria
- With no human trigger, the scheduled job runs the import on its configured interval and
  the catalogue reflects the source as of that run. ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #11)
- A scheduled run overlapping another in-progress import does not start a second concurrent
  run — it honours the single-run guard. (SPEC-0004 #12)
- The schedule is set from configuration, defaulting to a single daily overnight run.
