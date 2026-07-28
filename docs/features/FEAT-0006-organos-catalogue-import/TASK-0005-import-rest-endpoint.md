---
feat: FEAT-0006
domain: backend
adrs: [0002, 0005, 0006, 0010]
status: done
depends_on: [TASK-0002, TASK-0004]
---

# Import REST endpoint

The `ADMIN`-only import trigger, authored contract-first. Governed by
[ADR-0006](../../architecture/0006-reserved-api-url-prefix.md) (reserved `/api/` prefix),
[ADR-0010](../../architecture/0010-design-first-openapi-contract.md) (OpenAPI-first), and
[ADR-0005](../../architecture/0005-session-based-authentication.md) (session security).

This feature exposes **no read endpoint**: the authenticated read of the catalogue is
FEAT-0007's `GET /api/organos`, which serves each Órgano with its taxonomy placement.

## Scope
- Author the request/response contract in
  [`docs/api/openapi.yaml`](../../api/openapi.yaml) **before** implementing the controller.
- `POST /api/admin/organos/import` — `@Secured("ADMIN")`: runs `ImportOrganos` and returns
  the `ImportOutcome` (success + added/refreshed/deactivated counts, or "already
  running").

## Acceptance criteria
- As an `ADMIN`, `POST /api/admin/organos/import` runs an import and returns success with
  added/refreshed/deactivated counts; when an import is already running it returns the
  "already running" outcome rather than starting a second. ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #10, #12)
- A `USER` or unauthenticated caller to `POST /api/admin/organos/import` is denied
  (403 / 401). (SPEC-0004 #1)
- The implementation conforms to `docs/api/openapi.yaml` (enforced by the CI contract
  test), and the endpoint is integration-tested over HTTP.
