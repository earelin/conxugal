---
feat: FEAT-0007
domain: backend
adrs: [0002, 0005, 0006, 0010, 0012, 0016]
status: todo
depends_on: [TASK-0005]
---

# Taxonomy management & classification endpoints

The six `ADMIN` write operations, authored contract-first, over the use cases from
[TASK-0003](TASK-0003-taxonomy-management-use-cases.md) and
[TASK-0004](TASK-0004-organo-classification-use-cases.md). Governed by the same records as
[TASK-0005](TASK-0005-taxonomy-read-endpoints.md), with
[ADR-0005](../../architecture/0005-session-based-authentication.md) carrying the weight
here: this is the whole `ADMIN` surface of the feature.

## Scope
- Author every contract in [`docs/api/openapi.yaml`](../../api/openapi.yaml) **before**
  implementing the controllers. The paths are fixed by the feature's *API surface*, not
  chosen here:

  | Operation | Path |
  | --- | --- |
  | Create a node (root or under a parent) | `POST /api/admin/taxonomy-nodes` |
  | Rename a node | `PATCH /api/admin/taxonomy-node/{id}` |
  | Move a node (re-parent, or to the root) | `PUT /api/admin/taxonomy-node/{id}/parent` |
  | Delete a node | `DELETE /api/admin/taxonomy-node/{id}` |
  | Place an Órgano in a node | `PUT /api/admin/organo/{id}/taxonomy-node` |
  | Clear an Órgano's placement | `DELETE /api/admin/organo/{id}/taxonomy-node` |

- All six are `@Secured("ADMIN")`. Note the last two hang off `/api/organo/{id}` — they
  change the Órgano, not the node — so an `/api/admin/taxonomy-node/**`-shaped security
  rule would miss them.
- Request records validated at the edge: `@NotBlank @Size(max = 255)` on a node name, per
  the `CreateUserRequest` precedent. A null `parentId` is **valid** on create and move — it
  means "at the root" — and must not be conflated with a missing field.
- Map each domain rejection to its own status **and** its own RFC 9457 problem `type`, as
  fixed by the feature's failure contract. Status alone is not enough: a cycle and a
  blocked-by-children delete are both 409, and
  [TASK-0008](TASK-0008-taxonomy-management-ui.md) must tell them apart to show the right
  message.

  | Problem type | Status |
  | --- | --- |
  | `urn:conxugal:problem-type:taxonomy-node-not-found` | 404 |
  | `urn:conxugal:problem-type:organo-not-found` | 404 |
  | `urn:conxugal:problem-type:taxonomy-cycle` | 409 |
  | `urn:conxugal:problem-type:taxonomy-node-has-children` | 409 |
  | `urn:conxugal:problem-type:duplicate-sibling-name` | 409 |

- Every operation carries the ADR-0012 rate-limit contract — the three `RateLimit-*`
  headers and the shared `TooManyRequests` 429.

## Acceptance criteria
- As an `ADMIN`, a node can be created at the root and under a parent, renamed, moved and
  deleted over HTTP; each change is visible in the next `GET /api/taxonomy-nodes`.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #14)
- A move that would create a cycle is refused with 409 and the taxonomy is unchanged; a
  delete of a node with children is refused likewise — and the two carry **different**
  problem `type`s, so a client can distinguish them without parsing prose.
  (SPEC-0004 #15, #16)
- Naming a node the same as an existing sibling is refused with 409 and its own problem
  type; the same name under a different parent succeeds. (SPEC-0004 #14)
- Every operation naming an unknown node or an unknown Órgano returns 404 with the matching
  problem type — including a move onto an unknown parent, which must not surface as a 500.
- As an `ADMIN`, assigning an Órgano to a node then to another leaves it in only the
  second; clearing leaves it in none — `GET /api/organos` reflects each step in that
  Órgano's `taxonomyNodeId`, ending null. (SPEC-0004 #17, #18)
- Deleting a node with Órganos placed in it leaves those Órganos in `GET /api/organos` with
  a null `taxonomyNodeId` — none is deleted. (SPEC-0004 #16)
- A `USER` is denied on **all six** (403), and an unauthenticated caller likewise (401) —
  asserted per operation, since a security rule that misses the two `/api/organo/{id}/…`
  paths would leave classification open to any signed-in user. (SPEC-0004 #1)
- The implementation conforms to `docs/api/openapi.yaml`, and the endpoints are
  integration-tested over HTTP against a running server.
