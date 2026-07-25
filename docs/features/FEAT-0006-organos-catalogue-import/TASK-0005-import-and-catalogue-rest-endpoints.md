---
feat: FEAT-0006
domain: backend
adrs: [0002, 0005, 0006, 0010]
status: todo
depends_on: [TASK-0002, TASK-0004]
---

# Import + catalogue REST endpoints

The `ADMIN`-only import trigger and the authenticated catalogue-read endpoint, authored
contract-first. Governed by [ADR-0006](../../architecture/0006-reserved-api-url-prefix.md)
(reserved `/api/` prefix), [ADR-0010](../../architecture/0010-design-first-openapi-contract.md)
(OpenAPI-first), and [ADR-0005](../../architecture/0005-session-based-authentication.md)
(session security).

## Scope
- Author the request/response contract for both endpoints in
  [`docs/api/openapi.yaml`](../../api/openapi.yaml) **before** implementing the
  controllers.
- `POST /api/admin/organos/import` — `@Secured("ADMIN")`: runs `ImportOrganos` and returns
  the `ImportOutcome` (success + added/refreshed/deactivated counts, or "already
  running").
- `GET /api/organos` — `@Secured(IS_AUTHENTICATED)`: lists the stored catalogue (each
  body's name and active state) for any authenticated user. Deliberately **not** under
  `/api/admin/`.

## Acceptance criteria
- As an `ADMIN`, `POST /api/admin/organos/import` runs an import and returns success with
  added/refreshed/deactivated counts; when an import is already running it returns the
  "already running" outcome rather than starting a second. ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #10, #12)
- A `USER` or unauthenticated caller to `POST /api/admin/organos/import` is denied
  (403 / 401). (SPEC-0004 #1)
- As an authenticated `USER` or `ADMIN`, `GET /api/organos` returns the catalogue list
  with each body's name and active state. (SPEC-0004 #2, #3, #8)
- An unauthenticated caller to `GET /api/organos` is denied (401). (SPEC-0004 #2)
- The implementation conforms to `docs/api/openapi.yaml` (enforced by the CI contract
  test), and the endpoints are integration-tested over HTTP.
