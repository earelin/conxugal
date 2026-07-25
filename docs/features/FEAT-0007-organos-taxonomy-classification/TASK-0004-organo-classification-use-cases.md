---
feat: FEAT-0007
domain: backend
adrs: [0002]
status: todo
depends_on: [TASK-0001]
---

# Órgano classification use cases

Placing an Órgano in a node, taking it out, reading the unclassified worklist, and
assembling the tree the read endpoint serves. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md), over the ports from
[TASK-0001](TASK-0001-taxonomy-node-domain-model-and-placement.md).

## Scope
- `AssignOrganoToNode` — sets an Órgano's placement to a node, **replacing** any current
  one; rejects an unknown Órgano or an unknown node.
- `ClearOrganoNode` — removes an Órgano's placement, returning it to unclassified.
- `ListUnclassifiedOrganos` — the Órganos with no placement, the worklist an admin files
  from.
- `GetTaxonomyTree` — reads the nodes and the placed Órganos and assembles the tree the
  authenticated read endpoint returns: each node with its children and the Órganos placed
  **directly** in it. An Órgano appears once, under its own node — a node does not inherit
  its descendants' Órganos.
- Assemble the tree from whole-collection reads rather than a query per node, so tree depth
  costs no extra round trips.

## Acceptance criteria
- Assigning an Órgano to a node, then to a different node, leaves it placed in only the
  second; it is never in two at once.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #17)
- Clearing an assignment leaves the Órgano in no node, and it reappears in the unclassified
  listing. (SPEC-0004 #17, #18)
- An Órgano that has never been classified — including a newly imported one — is in the
  unclassified listing until it is assigned. (SPEC-0004 #18)
- Assigning to an unknown node, or assigning an unknown Órgano, is rejected and writes
  nothing.
- `GetTaxonomyTree` returns every node in its parent/child position, with each node's
  directly-placed Órganos and their active state, and an **empty tree when no node
  exists**. Unclassified Órganos are not in the tree; they are read through the
  unclassified listing and `GET /api/organos`. (SPEC-0004 #9)
- Unit-tested against test doubles of the ports, including the empty-taxonomy and
  reassignment cases.
