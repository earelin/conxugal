---
feat: FEAT-0009
domain: backend
adrs: [0002, 0005, 0006, 0010, 0011, 0012, 0017, 0019, 0020]
status: todo
depends_on: [TASK-0002, TASK-0010]
---

# Triggers and the run read

The two import triggers, the run read that makes them answerable, and the wiring that turns a
mark into an import. Authored contract-first
([ADR-0010](../../architecture/0010-design-first-openapi-contract.md)) under the reserved
`/api/` prefix ([ADR-0006](../../architecture/0006-reserved-api-url-prefix.md)), named per
[ADR-0020](../../architecture/0020-actions-as-verbs-in-rest-paths.md), secured per
[ADR-0005](../../architecture/0005-session-based-authentication.md), carrying
[ADR-0012](../../architecture/0012-rate-limit-http-contract.md)'s rate-limit contract.

Without the run read this feature would ship an importer whose outcome nobody — not even the
administrator who triggered it — could see; it is what makes #29 and #30 provable here at all.
SPEC-0007's run list, filters, diagnostics and live progress supersede it later, **over the same
rows**.

## Scope
- Author every contract in [`docs/api/openapi.yaml`](../../api/openapi.yaml) first.

  | Operation | Path | Success |
  | --- | --- | --- |
  | Import every marked, active Órgano | `POST /api/admin/contratos-menores/import` | 202 + run id |
  | Import one named Órgano | `POST /api/admin/organo/{id}/contratos-menores/import` | 202 + run id |
  | Read one run | `GET /api/admin/import-run/{id}` | 200 |

- **A trigger is asynchronous.** An initial import runs for days, so no trigger can carry R20's
  outcome in its response: each calls TASK-0010's claim, returns `202` with the run's identifier
  (and a `Location` pointing at the run read), and submits the execution.
- **Execution runs on a dedicated single-thread virtual-thread executor**, configured under
  `micronaut.executors` following the `organos-import` precedent
  ([ADR-0011](../../architecture/0011-blocking-io-virtual-threads.md)). A multi-day job must not
  occupy request-serving capacity, and the comment already in `application.yml` about not
  declaring a `blocking` executor applies unchanged.
- **The two refusals are `409` with distinct problem types**, because nothing was written and
  the request genuinely did not happen:

  | Problem type | Status | Meaning |
  | --- | --- | --- |
  | `urn:conxugal:problem-type:import-already-running` | 409 | Another import holds R22's guard |
  | `urn:conxugal:problem-type:organo-not-eligible` | 409 | The named Órgano is inactive or unmarked |
  | `urn:conxugal:problem-type:organo-not-found` | 404 | Unknown Órgano id (the existing type) |

  The `409` documents **which** types it can carry: a client that can only read the status
  cannot tell *wait* from *mark the Órgano*, which is exactly the distinction #34 requires and
  the UI renders differently.
- **`PUT /api/admin/organo/{id}/importable` changes shape, and not to a `409`.** The mark is
  written whether or not an import starts, so the response becomes `200` with a body carrying
  the run identifier when one started, or the refusal reason when none did. A `409` here would
  tell the client the mark did not apply — the opposite of SPEC-0005 #33's first clause, where
  a mark landing while an import runs is *refused rather than queued*, the mark itself
  standing. The mark triggers a **single-Órgano** import, never a sweep.
  `DELETE` is unchanged at `204`; it stops a run for that Órgano through TASK-0010's
  batch-boundary check, not synchronously.
- **`GET /api/admin/import-run/{id}`** returns the run's verdict — in progress, succeeded,
  failed, **partially succeeded**, and **abandoned** — its times, its total added and refreshed
  counts, and the covered Órganos each with its own state and counts. 404 on an unknown id.
- **Abandoned is part of the verdict set, not an implementation detail** — a **derived** one:
  the row still says `IN_PROGRESS`, and TASK-0007's single read rule is what turns it into
  *abandoned* on the way out. That is precisely the run an administrator goes looking at — a
  multi-day import whose process died — so reporting it as still in progress would leave the one
  question this read exists to answer unanswered. The schema names the verdict and TASK-0012
  renders it.
- **The outcome is a new schema, not the shipped `ImportOutcome`.** That one enumerates
  `[SUCCESS, ALREADY_RUNNING]` only, reports failure as a 500 problem, and caps its counts at
  100 000 — a bound SERGAS alone breaks by an order of magnitude. The new schema takes the full
  verdict set and no such cap. `ImportOutcome` stays as it is for the catalogue import.
- **Identifier wrappers stop at this boundary.** `ImportRunId` and the Órgano's id are domain
  types; the request and response records here carry a **plain UUID**, and the controller wraps
  the path variable on the way in and unwraps on the way out
  ([ADR-0019](../../architecture/0019-typed-aggregate-identifiers.md)). Nothing in
  `openapi.yaml` changes, and no wrapper is ever serialised — a `{"value": "…"}` in a response
  body means one leaked.
- All three operations are `@Secured("ADMIN")`; note the single-Órgano trigger hangs off
  `/api/admin/organo/{id}/…`, as the mark and FEAT-0007's classification writes do. Every
  operation declares ADR-0012's rate-limit headers and the shared 429, plus 400/401/403/500.

## Acceptance criteria
- As an `ADMIN`, each trigger returns `202` with a run identifier, and
  `GET /api/admin/import-run/{id}` on that identifier reports the run in progress and then its
  verdict, the covered Órganos, which of them failed, and the contracts added and refreshed.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #29, initial/resumed
  modes only)
- A run whose last advance predates the abandonment bound reads as **abandoned**, not as in
  progress, with the counts it reached intact.
- A run covering several Órganos in which one fails reads as **partially succeeded** and names
  the failing Órgano — not a bare success and not a bare failure. (SPEC-0005 #30)
- A trigger arriving while any import runs — of either importer — returns `409` with
  `import-already-running`, starts nothing, and is neither queued nor silently dropped.
  (SPEC-0005 #32)
- A single-Órgano trigger naming an inactive or unmarked Órgano returns `409` with
  `organo-not-eligible` — **a different problem type** from the guard refusal, distinguishable
  without parsing prose. (SPEC-0005 #34)
- Marking an Órgano while no import runs starts an import of **that Órgano alone** and answers
  with its run identifier; marking one while an import runs **keeps the mark** and answers with
  the guard refusal, and the Órgano reads as marked afterwards. (SPEC-0005 #1 trigger half, #5
  immediate half, #33 **refused-and-kept half** — its *next scheduled run* clause waits on the
  incremental feature)
- The trigger's response arrives without waiting for the import, and the import proceeds off the
  request-serving pool. (SPEC-0005 #29)
- `GET /api/admin/import-run/{id}` on an unknown identifier returns 404; a `USER` and an
  unauthenticated caller are denied on all three operations (403 / 401), asserted per operation.
  (SPEC-0005 #1)
- Every identifier on the wire is a plain UUID string — the run id a trigger returns is the one
  `GET /api/admin/import-run/{id}` accepts verbatim, with no wrapper object anywhere in a
  request or response body.
- The implementation conforms to `docs/api/openapi.yaml` (CI contract test), and every operation
  is integration-tested over HTTP against a running server with the source stubbed.
