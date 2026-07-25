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

## Scope
- Author every contract in [`docs/api/openapi.yaml`](../../api/openapi.yaml) **before**
  implementing the controllers.
- `GET /api/organos/taxonomy` — `@Secured(IS_AUTHENTICATED)`: the payload from
  `GetTaxonomyTree` — each node with its child nodes and the Órganos placed in it (id,
  name, active state), **plus the unclassified Órganos** as a sibling collection. It is the
  **only** read of the catalogue: one call fills a filter tree and covers the SPEC-0004 R8
  view. Not under `/api/admin/` — reading the catalogue is a user capability.
- **No flat `GET /api/organos`** is added, here or in FEAT-0006; there is no second shape of
  the same Órganos to keep in step.
- `ADMIN`-only, under `/api/admin/`: create a node, rename it, move it, delete it; assign
  an Órgano to a node and clear its node. No separate unclassified listing — the admin
  worklist is the unclassified collection of the read above.
- Map the TASK-0003 domain rejections onto distinct statuses — unknown node/Órgano to 404,
  a cycle on move and a delete blocked by children to 409 — each with a body that says
  which rule refused, so the admin UI can show a real message.

## Acceptance criteria
- As an authenticated `USER` or `ADMIN`, `GET /api/organos/taxonomy` returns the tree with
  each node's children and its directly-placed Órganos, plus the unclassified ones; with no
  node created it returns an empty tree and the whole catalogue as unclassified.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #2, #9)
- An unauthenticated caller to `GET /api/organos/taxonomy` is denied (401).
  (SPEC-0004 #2)
- That one response shows, for every stored Órgano, its name, its active state, and its
  taxonomy placement or that it is unclassified — nothing in the catalogue is unreachable
  through it. (SPEC-0004 #8)
- As an `ADMIN`, a node can be created at the root and under a parent, renamed, moved and
  deleted over HTTP. (SPEC-0004 #14)
- A move that would create a cycle is refused with a distinct status and the taxonomy is
  unchanged; a delete of a node with children is refused likewise. (SPEC-0004 #15, #16)
- As an `ADMIN`, assigning an Órgano to a node then to another leaves it in only the
  second; clearing leaves it in none; the read response reflects both — the Órgano moves
  between nodes, and back into the unclassified collection when cleared.
  (SPEC-0004 #17, #18)
- A `USER` or unauthenticated caller to **any** of the management endpoints — node
  create/rename/move/delete, assign/clear — is denied (403 / 401). (SPEC-0004 #1)
- The implementation conforms to `docs/api/openapi.yaml` (enforced by the CI contract
  test), and the endpoints are integration-tested over HTTP.
