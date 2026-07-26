---
feat: FEAT-0007
domain: backend
adrs: [0002, 0005, 0006, 0010]
status: todo
depends_on: [TASK-0002, TASK-0003, TASK-0004]
---

# Taxonomy & classification REST endpoints

The two authenticated reads and the `ADMIN` management surface, authored contract-first.
Governed by [ADR-0006](../../architecture/0006-reserved-api-url-prefix.md) (reserved
`/api/` prefix), [ADR-0010](../../architecture/0010-design-first-openapi-contract.md)
(OpenAPI-first), and [ADR-0005](../../architecture/0005-session-based-authentication.md)
(session security).

## Scope
- Author every contract in [`docs/api/openapi.yaml`](../../api/openapi.yaml) **before**
  implementing the controllers.
- `GET /api/organos` — `@Secured(IS_AUTHENTICATED)`: a **flat array** of every Órgano —
  `id`, `name`, `active`, `taxonomyNodeId` (nullable). This is the SPEC-0004 R8 catalogue
  view. Not under `/api/admin/` — reading the catalogue is a user capability.
- `GET /api/organos/taxonomy` — `@Secured(IS_AUTHENTICATED)`: a **flat array** of every
  taxonomy node — `id`, `name`, `parentId` (nullable, null for a root). No Órganos, no
  nested children.
- Neither response nests, groups, or partitions anything: each is one use-case list mapped
  row-for-row onto a response record. The client joins them on `taxonomyNodeId` = node `id`
  to build the tree, and filters `taxonomyNodeId == null` for the unclassified set.
- Both are unfiltered and unpaged, and take no query parameters — the whole table, every
  time. Adding a filter later is additive; guessing at one now is not.
- `ADMIN`-only, under `/api/admin/`: create a node, rename it, move it, delete it; assign
  an Órgano to a node and clear its node.
- Map the TASK-0003 domain rejections onto distinct statuses — unknown node/Órgano to 404,
  a cycle on move and a delete blocked by children to 409 — each with a body that says
  which rule refused, so the admin UI can show a real message.

## Acceptance criteria
- As an authenticated `USER` or `ADMIN`, `GET /api/organos` returns every stored Órgano with
  its name, its active state, and its `taxonomyNodeId` or null — so nothing in the catalogue
  is unreachable through it.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #2, #8)
- As an authenticated `USER` or `ADMIN`, `GET /api/organos/taxonomy` returns every node with
  its `parentId` or null; with no node created it returns an empty array, while
  `GET /api/organos` still returns the whole catalogue. (SPEC-0004 #2, #8, #9)
- An unauthenticated caller to either read is denied (401). (SPEC-0004 #2)
- As an `ADMIN`, a node can be created at the root and under a parent, renamed, moved and
  deleted over HTTP; each change is visible in the next `GET /api/organos/taxonomy`.
  (SPEC-0004 #14)
- A move that would create a cycle is refused with a distinct status and the taxonomy is
  unchanged; a delete of a node with children is refused likewise. (SPEC-0004 #15, #16)
- As an `ADMIN`, assigning an Órgano to a node then to another leaves it in only the
  second; clearing leaves it in none — `GET /api/organos` reflects each step in that
  Órgano's `taxonomyNodeId`, ending null. (SPEC-0004 #17, #18)
- Deleting a node with Órganos placed in it leaves those Órganos in `GET /api/organos` with
  a null `taxonomyNodeId` — none is deleted. (SPEC-0004 #16)
- A `USER` or unauthenticated caller to **any** of the management endpoints — node
  create/rename/move/delete, assign/clear — is denied (403 / 401). (SPEC-0004 #1)
- The implementation conforms to `docs/api/openapi.yaml` (enforced by the CI contract
  test), and the endpoints are integration-tested over HTTP.
