---
feat: FEAT-0007
domain: backend
adrs: [0002, 0005, 0006, 0010]
status: todo
depends_on: [TASK-0002, TASK-0003, TASK-0004]
---

# Taxonomy & classification REST endpoints

The authenticated tree read and the `ADMIN` management surface, authored contract-first.
Governed by [ADR-0006](../../architecture/0006-reserved-api-url-prefix.md) (reserved
`/api/` prefix), [ADR-0010](../../architecture/0010-design-first-openapi-contract.md)
(OpenAPI-first), and [ADR-0005](../../architecture/0005-session-based-authentication.md)
(session security).

Also needs
[FEAT-0006 TASK-0005](../FEAT-0006-organos-catalogue-import/TASK-0005-import-and-catalogue-rest-endpoints.md),
which introduces `GET /api/organos`; the placement field below is added to that response.

## Scope
- Author every contract in [`docs/api/openapi.yaml`](../../api/openapi.yaml) **before**
  implementing the controllers.
- `GET /api/organos/taxonomy` — `@Secured(IS_AUTHENTICATED)`: the tree from
  `GetTaxonomyTree`, each node carrying its child nodes and the Órganos placed in it (id,
  name, active state), so one call fills a filter tree. Not under `/api/admin/` — reading
  the taxonomy is a user capability.
- `GET /api/organos` — extend the FEAT-0006 response with each Órgano's **placement**: its
  taxonomy node, or the absence of one. This is what completes the SPEC-0004 R8 view.
- `ADMIN`-only, under `/api/admin/`: create a node, rename it, move it, delete it; assign
  an Órgano to a node and clear its node; list the unclassified Órganos.
- Map the TASK-0003 domain rejections onto distinct statuses — unknown node/Órgano to 404,
  a cycle on move and a delete blocked by children to 409 — each with a body that says
  which rule refused, so the admin UI can show a real message.

## Acceptance criteria
- As an authenticated `USER` or `ADMIN`, `GET /api/organos/taxonomy` returns the tree with
  each node's children and its directly-placed Órganos; with no node created it returns an
  empty tree.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #2, #9)
- An unauthenticated caller to `GET /api/organos/taxonomy` is denied (401).
  (SPEC-0004 #2)
- `GET /api/organos` shows, for every Órgano, its name, its active state, and its taxonomy
  placement or that it is unclassified. (SPEC-0004 #8)
- As an `ADMIN`, a node can be created at the root and under a parent, renamed, moved and
  deleted over HTTP. (SPEC-0004 #14)
- A move that would create a cycle is refused with a distinct status and the taxonomy is
  unchanged; a delete of a node with children is refused likewise. (SPEC-0004 #15, #16)
- As an `ADMIN`, assigning an Órgano to a node then to another leaves it in only the
  second; clearing leaves it in none; the unclassified listing reflects both.
  (SPEC-0004 #17, #18)
- A `USER` or unauthenticated caller to **any** of the management endpoints — node
  create/rename/move/delete, assign/clear, unclassified listing — is denied (403 / 401).
  (SPEC-0004 #1)
- The implementation conforms to `docs/api/openapi.yaml` (enforced by the CI contract
  test), and the endpoints are integration-tested over HTTP.
