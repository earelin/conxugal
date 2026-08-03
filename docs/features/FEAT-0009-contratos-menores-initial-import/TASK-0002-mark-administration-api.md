---
feat: FEAT-0009
domain: backend
adrs: [0002, 0005, 0006, 0010, 0012, 0020]
status: todo
depends_on: [TASK-0001]
---

# Mark administration API

The `ADMIN` surface over [TASK-0001](TASK-0001-import-mark-on-organo-catalogue.md)'s mark:
two write operations and the administrator's catalogue read. Authored contract-first in
[`docs/api/openapi.yaml`](../../api/openapi.yaml)
([ADR-0010](../../architecture/0010-design-first-openapi-contract.md)), under the reserved
`/api/` prefix ([ADR-0006](../../architecture/0006-reserved-api-url-prefix.md)), named per
[ADR-0020](../../architecture/0020-actions-as-verbs-in-rest-paths.md), secured per
[ADR-0005](../../architecture/0005-session-based-authentication.md) and carrying
[ADR-0012](../../architecture/0012-rate-limit-http-contract.md)'s rate-limit contract.

**Prerequisites outside this feature.** Two FEAT-0007 tasks this one builds on are still
`todo`, and neither artefact exists in `openapi.yaml` today:
[TASK-0005](../FEAT-0007-organos-taxonomia-classification/TASK-0005-taxonomia-read-endpoints.md)
defines `GET /api/organos` and the Órgano shape this read mirrors, and
[TASK-0006](../FEAT-0007-organos-taxonomia-classification/TASK-0006-taxonomia-admin-endpoints.md)
introduces the `urn:conxugal:problem-type:organo-not-found` type reused below. Build this after
them, or the shape and the problem type are being invented twice.

**Marking triggers nothing yet.** There is no importer until
[TASK-0010](TASK-0010-multi-organo-orchestration.md), so the `PUT` here only writes the mark;
[TASK-0011](TASK-0011-triggers-and-run-read.md) is where it starts an import and where its
response body grows to say whether one started. That change is stated in both tasks so it is
expected rather than discovered.

## Scope
- Author every contract in `openapi.yaml` **before** implementing the controllers.

  | Operation | Path | Success |
  | --- | --- | --- |
  | Mark an Órgano for import | `PUT /api/admin/organo/{id}/importable` | 204 |
  | Unmark it | `DELETE /api/admin/organo/{id}/importable` | 204 |
  | The administrator's catalogue read | `GET /api/admin/organos` | 200 |

- `MarkOrganoForImport` and `UnmarkOrganoForImport` use cases in `domain`, over
  `OrganoRepository`. Two use cases rather than one flag-setter, on FEAT-0007's own reasoning
  for `PUT`/`DELETE` on the taxonomy placement: they gain genuinely different rules in
  TASK-0011, where marking requests an import and can have that import refused while unmarking
  stops one.
- Both are idempotent: marking an already-marked Órgano, or unmarking an unmarked one,
  succeeds and changes nothing.
- An unknown Órgano id is 404 with `urn:conxugal:problem-type:organo-not-found` — the type
  FEAT-0007's classification endpoints define for the same id, reused rather than duplicated.
- `GET /api/admin/organos` serves the catalogue **as an administrator sees it**: the same
  Órgano shape FEAT-0007's `GET /api/organos` will serve — id, name, active state, taxonomy
  placement — **plus `importable`**, in the same name order. It is a second serialisation of an
  Órgano, which breaks FEAT-0007's "exactly one endpoint serialises an Órgano" rule; the trade
  is the feature's, taken knowingly (feature *API surface*), and it must carry the placement
  as well as the mark so the admin section can swap one read for the other rather than issue
  both.
  - It carries **no import state** yet — `MARCADO` / `PARCIAL` / `IMPORTADO` needs the
    three-state fact, which [TASK-0006](TASK-0006-per-organo-import-state.md) builds and adds
    to this same response.
- All three are `@Secured("ADMIN")`. Note the two writes hang off `/api/admin/organo/{id}`,
  the path FEAT-0007's classification writes also use, so a security rule shaped around
  `/api/admin/organos/**` would miss them.
- Every operation declares ADR-0012's three `RateLimit-*` headers and the shared
  `TooManyRequests` 429, plus the 400, 401, 403 and 500 responses the shared ruleset requires.

## Acceptance criteria
- As an `ADMIN`, marking an Órgano then reading `GET /api/admin/organos` shows it marked;
  unmarking it and re-reading shows it unmarked; an Órgano never marked reads as unmarked.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #4)
- Marking an already-marked Órgano and unmarking an unmarked one both succeed without
  changing anything else on the row. (SPEC-0005 #4)
- `GET /api/admin/organos` returns every Órgano — active and inactive, classified and not —
  in name order, each with its taxonomy placement and its mark. (SPEC-0005 #4)
- Either write against an unknown Órgano id returns 404 carrying
  `urn:conxugal:problem-type:organo-not-found`, not a 500.
- A `USER` is denied on all three (403) and an unauthenticated caller likewise (401),
  asserted per operation. (SPEC-0005 #1)
- The implementation conforms to `docs/api/openapi.yaml` (CI contract test), and all three
  operations are integration-tested over HTTP against a running server.
