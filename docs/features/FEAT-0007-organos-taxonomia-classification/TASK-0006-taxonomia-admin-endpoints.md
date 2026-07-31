---
feat: FEAT-0007
domain: backend
adrs: [0002, 0005, 0006, 0010, 0012, 0016]
status: todo
depends_on: [TASK-0002, TASK-0003, TASK-0004, TASK-0005]
---

# Taxonomía management & classification endpoints

The six `ADMIN` write operations, authored contract-first, over the use cases from
[TASK-0003](TASK-0003-taxonomia-management-use-cases.md) and
[TASK-0004](TASK-0004-organo-classification-use-cases.md). Governed by the same records as
[TASK-0005](TASK-0005-taxonomia-read-endpoints.md), with
[ADR-0005](../../architecture/0005-session-based-authentication.md) carrying the weight
here: this is the whole `ADMIN` surface of the feature.

## Scope
- Author every contract in [`docs/api/openapi.yaml`](../../api/openapi.yaml) **before**
  implementing the controllers. The paths are fixed by the feature's *API surface*, not
  chosen here:

  | Operation | Path |
  | --- | --- |
  | Create a term (root or under a parent) | `POST /api/admin/organos/taxonomia/termos` |
  | Rename a term | `PATCH /api/admin/organos/taxonomia/termo/{id}` |
  | Move a term (re-parent, or to the root) | `PUT /api/admin/organos/taxonomia/termo/{id}/parent` |
  | Delete a term | `DELETE /api/admin/organos/taxonomia/termo/{id}` |
  | Place an Órgano in a term | `PUT /api/admin/organo/{id}/termo` |
  | Clear an Órgano's placement | `DELETE /api/admin/organo/{id}/termo` |

- All six are `@Secured("ADMIN")`. Note the last two hang off `/api/admin/organo/{id}` —
  they change the Órgano, not the term — so an `/api/admin/organos/taxonomia/**`-shaped security
  rule would miss them.
- Success responses, fixed by the feature's *API surface*: **201** with the created term for
  the create (the id is required — TASK-0008 selects and expands the new term without a
  refetch), **200** with the updated term for the rename, **204** for the other four. The
  two request bodies are `{parentId}` on the move and `{termoId}` on the placement.
- Request records validated at the edge: `@NotBlank @Size(max = 255)` on a term name, per
  the `CreateUserRequest` precedent. A null `parentId` is **valid** on create and move — it
  means "at the root" — and must not be conflated with a missing field. Whatever
  representation is chosen, it must round-trip a null distinctly enough that "move this term
  to the root" stays expressible.
- Map each domain rejection to its own status **and** its own RFC 9457 problem `type`, as
  fixed by the feature's failure contract. Status alone is not enough: a cycle and a
  blocked-by-children delete are both 409, and
  [TASK-0008](TASK-0008-taxonomia-management-ui.md) must tell them apart to show the right
  message.

  | Problem type | Status |
  | --- | --- |
  | `urn:conxugal:problem-type:termo-not-found` | 404 |
  | `urn:conxugal:problem-type:organo-not-found` | 404 |
  | `urn:conxugal:problem-type:termo-cycle` | 409 |
  | `urn:conxugal:problem-type:termo-has-children` | 409 |
  | `urn:conxugal:problem-type:duplicate-sibling-name` | 409 |

- Each documented 404 and 409 **enumerates which problem `type`s it can carry**. The shared
  `Error` schema has no `type` enum, so a 409 described only as "conflict" leaves a
  generated client unable to tell a cycle from a duplicate name — defeating the distinction
  the five types exist to make.
- Every operation carries the ADR-0012 rate-limit contract — the three `RateLimit-*`
  headers and the shared `TooManyRequests` 429 — plus the 400, 401 and 500 responses
  `vacuum:owasp` requires.
- The two refusals that can also arrive as **constraint violations** rather than as a use
  case's own check — a raced duplicate name and an assign racing a delete — are **not**
  among these types. The adapter translates no SQLSTATE into a domain exception, so a write
  that loses one of those races surfaces as a **500**. That window is accepted against how
  rarely this taxonomy is written to (see the feature's *Edge cases*, *Concurrent edits to
  the tree*); it is noted here so it is not mistaken for a gap to close with per-SQLSTATE
  handling at this layer.

## Acceptance criteria
- As an `ADMIN`, a term can be created at the root and under a parent, renamed, moved and
  deleted over HTTP; each change is visible in the next `GET /api/organos/taxonomia`. The
  create returns 201 **carrying the new term's id**, which the UI needs to select it without
  a refetch; a move to the root succeeds with an explicit null parent.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #14)
- A move that would create a cycle is refused with 409 and the taxonomy is unchanged; a
  delete of a term with children is refused likewise — and the two carry **different**
  problem `type`s, so a client can distinguish them without parsing prose.
  (SPEC-0004 #15, #16)
- Naming a term the same as an existing sibling is refused with 409 and its own problem
  type; the same name under a different parent succeeds. (SPEC-0004 #14)
- Every operation naming an unknown term or an unknown Órgano returns 404 with the matching
  problem type — including a move onto an unknown parent, which must not surface as a 500.
- As an `ADMIN`, assigning an Órgano to a term then to another leaves it in only the
  second; clearing leaves it in none — `GET /api/organos` reflects each step in that
  Órgano's `termoId`, ending null. (SPEC-0004 #17, #18)
- Deleting a term with Órganos placed in it leaves those Órganos in `GET /api/organos` with
  a null `termoId` — none is deleted. (SPEC-0004 #16)
- A `USER` is denied on **all six** (403), and an unauthenticated caller likewise (401) —
  asserted per operation, since a security rule that misses the two `/api/organo/{id}/…`
  paths would leave classification open to any signed-in user. (SPEC-0004 #1)
- The implementation conforms to `docs/api/openapi.yaml`, and the endpoints are
  integration-tested over HTTP against a running server.
