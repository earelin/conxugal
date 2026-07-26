---
feat: FEAT-0007
domain: frontend
adrs: [0003, 0004, 0015]
status: todo
depends_on: [TASK-0006, TASK-0007]
---

# Classification UI

Filing Órganos into the taxonomy: the assign picker, the clear action, and the unclassified
worklist as a working queue. Built on
[TASK-0007](TASK-0007-organos-section-and-tree-view.md)'s section and builder, against the
two classification endpoints of
[TASK-0006](TASK-0006-taxonomy-admin-endpoints.md). Independent of
[TASK-0008](TASK-0008-taxonomy-management-ui.md) — the two touch different controls and can
be picked up in either order.

Visual target: [`design/assign-organo.svg`](design/assign-organo.svg) and the worklist in
[`design/unclassified-worklist.svg`](design/unclassified-worklist.svg).

## Scope
- `Asignar a un nodo` — a searchable tree picker, reachable from a worklist row and from a
  node's `Asignar órgano` action. The dialog always **replaces** a placement, never adds
  one: an already-classified Órgano shows its current node, and confirming moves it.
- `Quitar do nodo` on an Órgano row in a node's table — clears the placement and returns the
  Órgano to the worklist.
- The **Sen clasificar** worklist as the filing queue: the null-`taxonomyNodeId` slice of the
  catalogue TASK-0007 already fetched, never a separate call or an admin-only endpoint.
- After an assign or a clear, refetch the catalogue and re-run the builder. The taxonomy read
  is untouched by either, so it is not refetched.
- Refusals by problem `type`: `organo-not-found` and `taxonomy-node-not-found` (the target
  node was deleted by another admin) each get their own message and a refresh path.
- Inactive Órganos are dimmed but assignable — going inactive does not unfile an Órgano, and
  the existing table convention already dims rather than hides them.
- All copy in Galician, in the slice's strings.

## Acceptance criteria
- An `ADMIN` can assign an Órgano from the worklist to a node; it leaves the worklist and
  appears under that node in the same refresh.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #17, #18)
- Reassigning an already-classified Órgano moves it: it appears under the new node and
  under **no** other — the picker offers no way to place it in two. (SPEC-0004 #17)
- Clearing a placement returns the Órgano to the worklist, and it is not deleted.
  (SPEC-0004 #18)
- A newly imported Órgano is visible in the worklist without any extra action. (SPEC-0004 #18)
- Assigning to a node another admin has just deleted shows its own message and recovers on
  refresh. (SPEC-0004 #14)
- An inactive Órgano is visibly dimmed and can still be assigned and cleared. (SPEC-0004 #6)
- All added copy is in Galician. ([SPEC-0001](../../specs/SPEC-0001-web-ui.md) #6)
- Component-tested for assign, reassign, clear, the worklist contents and each refusal, with
  HTTP mocked with `nock` as the existing admin page tests do.
