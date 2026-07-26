---
feat: FEAT-0007
domain: backend
adrs: [0002, 0005, 0006, 0010, 0012, 0016]
status: todo
depends_on: [TASK-0004]
---

# Taxonomy & catalogue read endpoints

The two authenticated reads, authored contract-first. Governed by
[ADR-0006](../../architecture/0006-reserved-api-url-prefix.md) (reserved `/api/` prefix),
[ADR-0016](../../architecture/0016-rest-resource-naming.md) (plural for a collection,
singular for one element), [ADR-0010](../../architecture/0010-design-first-openapi-contract.md)
(OpenAPI-first), [ADR-0012](../../architecture/0012-rate-limit-http-contract.md) (the
rate-limit contract every operation carries), and
[ADR-0005](../../architecture/0005-session-based-authentication.md) (session security).

The `ADMIN` management and classification endpoints are
[TASK-0006](TASK-0006-taxonomy-admin-endpoints.md): a different security posture, a
different half of the contract, and six operations against these two.

## Scope
- Author both contracts in [`docs/api/openapi.yaml`](../../api/openapi.yaml) **before**
  implementing the controllers.
- `GET /api/organos` — `@Secured(IS_AUTHENTICATED)`: a **flat array** of every Órgano —
  `id`, `name`, `active`, `taxonomyNodeId` (nullable). This is the SPEC-0004 R8 catalogue
  view. Not under `/api/admin/` — reading the catalogue is a user capability.
- `GET /api/taxonomy-nodes` — `@Secured(IS_AUTHENTICATED)`: a **flat array** of every
  taxonomy node — `id`, `name`, `parentId` (nullable, null for a root). No Órganos, no
  nested children. **Not** `/api/organos/taxonomy`: that path parks a second resource on
  `/api/organos`' member slot, which is the collision ADR-0016 was written to stop.
- Both carry the ADR-0012 rate-limit contract — the three `RateLimit-*` response headers
  and the shared `TooManyRequests` 429 — like every other operation in the contract.
  Omitting them fails `openapi-lint` before any code runs.
- Neither response nests, groups, or partitions anything: each is one use-case list mapped
  row-for-row onto a response record. The client joins them on `taxonomyNodeId` = node `id`
  to build the tree, and filters `taxonomyNodeId == null` for the unclassified set.
- Both are unfiltered and unpaged, and take no query parameters — the whole table, every
  time. Adding a filter later is additive; guessing at one now is not.
- **Neither response guarantees an order**, and the OpenAPI description of each array says
  so — no `ORDER BY` is added to satisfy an unstated expectation. Presentation order belongs
  to the client ([TASK-0007](TASK-0007-organos-section-and-tree-view.md) sorts by name with
  locale-aware collation).

## Acceptance criteria
- As an authenticated `USER` or `ADMIN`, `GET /api/organos` returns every stored Órgano with
  its name, its active state, and its `taxonomyNodeId` or null — so nothing in the catalogue
  is unreachable through it.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #2, #8)
- As an authenticated `USER` or `ADMIN`, `GET /api/taxonomy-nodes` returns every node with
  its `parentId` or null, several levels of nesting arriving as a flat list the caller can
  rebuild the tree from. (SPEC-0004 #2, #14)
- With no node created, `GET /api/taxonomy-nodes` returns an empty array while
  `GET /api/organos` still returns the whole catalogue with every `taxonomyNodeId` null —
  the normal state after a first import, not a degenerate one. (SPEC-0004 #8)
- An unauthenticated caller to either read is denied (401). (SPEC-0004 #2)
- A `USER` is **allowed** on both — these are the two operations in this feature a non-admin
  may call, and gating them by mistake would take R8 with them. (SPEC-0004 #2, #8)
- Both responses carry the `RateLimit-*` headers, and the contract declares
  `TooManyRequests` for each.
- The implementation conforms to `docs/api/openapi.yaml`, and both endpoints are
  integration-tested over HTTP against a running server.
