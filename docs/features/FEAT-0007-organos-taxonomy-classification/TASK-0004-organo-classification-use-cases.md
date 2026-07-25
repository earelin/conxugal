---
feat: FEAT-0007
domain: backend
adrs: [0002]
status: todo
depends_on: [TASK-0001]
---

# Órgano classification use cases

Placing an Órgano in a node, taking it out, and assembling the payload the one
authenticated read serves — the tree plus the unclassified Órganos. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md), over the ports from
[TASK-0001](TASK-0001-taxonomy-node-domain-model-and-placement.md).

## Scope
- `AssignOrganoToNode` — sets an Órgano's placement to a node, **replacing** any current
  one; rejects an unknown Órgano or an unknown node.
- `ClearOrganoNode` — removes an Órgano's placement, returning it to unclassified.
- `GetTaxonomyTree` — reads the nodes and every Órgano and assembles what the authenticated
  endpoint returns: the tree, each node with its children and the Órganos placed
  **directly** in it, **plus the unclassified Órganos** as a sibling collection. An Órgano
  appears exactly once in the whole payload — under its own node or in the unclassified
  collection; a node does not inherit its descendants' Órganos.
- This is the only read of the catalogue, so the unclassified collection is not an admin
  convenience: without it a `USER` could not see an unclassified Órgano at all, and R8
  would be unmet.
- Assemble from whole-collection reads rather than a query per node, so tree depth costs no
  extra round trips.

## Acceptance criteria
- Assigning an Órgano to a node, then to a different node, leaves it placed in only the
  second; it is never in two at once.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #17)
- Clearing an assignment leaves the Órgano in no node, and it reappears in the unclassified
  collection. (SPEC-0004 #17, #18)
- An Órgano that has never been classified — including a newly imported one — is in the
  unclassified collection until it is assigned. (SPEC-0004 #18)
- Assigning to an unknown node, or assigning an unknown Órgano, is rejected and writes
  nothing.
- `GetTaxonomyTree` returns every node in its parent/child position with its directly-placed
  Órganos and their active state, and every unplaced Órgano in the unclassified collection —
  so **every stored Órgano is in the payload exactly once**, with its name, active state and
  placement. (SPEC-0004 #8, #9)
- With no node created, it returns an empty tree and the entire catalogue as unclassified.
  (SPEC-0004 #8)
- Unit-tested against test doubles of the ports, including the empty-taxonomy, the
  reassignment and the every-Órgano-appears-once cases.
