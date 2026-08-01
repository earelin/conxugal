---
feat: FEAT-0007
domain: backend
adrs: [0002, 0005, 0006, 0010, 0012, 0016]
status: done
depends_on: [TASK-0002, TASK-0004]
---

# Taxonomía & catalogue read endpoints

The two authenticated reads, authored contract-first. Governed by
[ADR-0006](../../architecture/0006-reserved-api-url-prefix.md) (reserved `/api/` prefix),
[ADR-0016](../../architecture/0016-rest-resource-naming.md) (plural for a collection,
singular for one element), [ADR-0010](../../architecture/0010-design-first-openapi-contract.md)
(OpenAPI-first), [ADR-0012](../../architecture/0012-rate-limit-http-contract.md) (the
rate-limit contract every operation carries), and
[ADR-0005](../../architecture/0005-session-based-authentication.md) (session security).

The `ADMIN` management and classification endpoints are
[TASK-0006](TASK-0006-taxonomia-admin-endpoints.md): a different security posture, a
different half of the contract, and six operations against these two.

## Scope
- Author both contracts in [`docs/api/openapi.yaml`](../../api/openapi.yaml) **before**
  implementing the controllers.
- `GET /api/organos` — `@Secured(IS_AUTHENTICATED)`: a **flat array** of every Órgano —
  `id`, `name`, `active`, `termoId` (nullable). This is the SPEC-0004 R8 catalogue
  view. Not under `/api/admin/` — reading the catalogue is a user capability.
- `GET /api/organos/taxonomia` — `@Secured(IS_AUTHENTICATED)`: a **flat array** of every
  term — `id`, `name`, `parentId` (nullable, null for a root). No Órganos, no
  nested children. **Not** `/api/organos/taxonomy`: that path parks a second resource on
  `/api/organos`' member slot, which is the collision ADR-0016 was written to stop.
- Both return **200** with a JSON array of the fields above; the schemas name every field's
  type and which are nullable. Both carry the ADR-0012 rate-limit contract — the three
  `RateLimit-*` response headers and the shared `TooManyRequests` 429 — plus the 400, 401
  and 500 responses `vacuum:owasp` requires, as `listUsers` already declares. Omitting any
  of these fails `openapi-lint` before a line of code runs.
- `TASK-0002`'s adapter is a real prerequisite, not just an ordering preference: the HTTP
  integration tests below need something to read from.
- Neither response nests, groups, or partitions anything: each is one use-case list mapped
  row-for-row onto a response record. The client joins them on `termoId` = term `id`
  to build the tree, and filters `termoId == null` for the unclassified set.
- Both are unfiltered and unpaged, and take no query parameters — the whole table, every
  time. Adding a filter later is additive; guessing at one now is not.
- **Both responses are ordered by name**, and the OpenAPI description of each array states
  it as a guarantee callers may rely on — not an incidental property. The ordering itself is
  the repository's, delivered by
  [TASK-0002](TASK-0002-taxonomia-store-infrastructure.md) under an explicit Galician
  collation; this task carries it into the contract and proves it over HTTP.
  [TASK-0007](TASK-0007-organos-section-and-tree-view.md) renders in the order it receives
  and does not re-sort.

## Acceptance criteria
- As an authenticated `USER` or `ADMIN`, `GET /api/organos` returns every stored Órgano with
  its name, its active state, and its `termoId` or null — so nothing in the catalogue
  is unreachable through it.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #2, #8)
- As an authenticated `USER` or `ADMIN`, `GET /api/organos/taxonomia` returns every term with
  its `parentId` or null, several levels of nesting arriving as a flat list the caller can
  rebuild the tree from. (SPEC-0004 #2 access-control half — the *browse the tree* half is
  deferred with #9 and no task here renders it)
- With no term created, `GET /api/organos/taxonomia` returns an empty array while
  `GET /api/organos` still returns the whole catalogue with every `termoId` null —
  the normal state after a first import, not a degenerate one. (SPEC-0004 #8)
- An unauthenticated caller to either read is denied (401). (SPEC-0004 #2)
- A `USER` is **allowed** on both — these are the two operations in this feature a non-admin
  may call, and gating them by mistake would take R8 with them. (SPEC-0004 #2, #8)
- Both responses carry the `RateLimit-*` headers, and the contract declares
  `TooManyRequests` for each.
- **Both responses arrive in name order**, asserted over HTTP against a fixture whose
  insertion order is deliberately not its name order — otherwise the assertion passes on an
  unordered query by luck.
- The order is correct for **accented Galician names**: a fixture containing `Á`, `Ñ` and
  plain-ASCII names comes back interleaved as a Galician reader expects, not with the
  accented ones trailing after `Z`. Under a C/POSIX collation this is the criterion that
  fails, and it is the reason the ordering is not left to the server's default.
- The OpenAPI description of each array **states the order as a guarantee**, so the contract
  and the behaviour cannot drift apart silently.
- The implementation conforms to `docs/api/openapi.yaml`, and both endpoints are
  integration-tested over HTTP against a running server.
