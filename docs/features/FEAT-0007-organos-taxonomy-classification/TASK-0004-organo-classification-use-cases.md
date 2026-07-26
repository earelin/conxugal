---
feat: FEAT-0007
domain: backend
adrs: [0002]
status: todo
depends_on: [TASK-0001]
---

# Órgano classification use cases + catalogue reads

Placing an Órgano in a node, taking it out, and the two whole-table reads the
authenticated endpoints serve. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md), over the ports from
[TASK-0001](TASK-0001-taxonomy-node-domain-model-and-placement.md).

## Scope
- `AssignOrganoToNode` — sets an Órgano's placement to a node, **replacing** any current
  one; rejects an unknown Órgano or an unknown node.
- `ClearOrganoNode` — removes an Órgano's placement, returning it to unclassified.
- `ListOrganos` — every stored Órgano, each with its name, active state and its
  `taxonomyNodeId` or null. A straight `findAll()`: no filtering, no paging, no grouping.
- `ListTaxonomyNodes` — every taxonomy node, each with its name and its `parentId` or null.
  Likewise a straight `findAll()`: **no tree is assembled here**, no descendant walk, and no
  node carries its children or its Órganos.
- Both reads mirror the `ListUsers` precedent in `gal.conxugal.domain.user` — a thin,
  single-purpose use case over the port, so the controller depends on the domain rather than
  on a repository.
- **No unclassified concept in the backend.** There is no `GetUnclassifiedOrganos` and no
  unclassified field in any result: an Órgano with a null `taxonomyNodeId` is unclassified,
  and callers filter for it. This is what the two-endpoint split buys — R8 and R18 are
  satisfied by the placement travelling on every Órgano, not by the server partitioning the
  catalogue.

## Acceptance criteria
- Assigning an Órgano to a node, then to a different node, leaves it placed in only the
  second; it is never in two at once.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #17)
- Clearing an assignment leaves the Órgano in no node — `ListOrganos` then reports a null
  `taxonomyNodeId` for it. (SPEC-0004 #17, #18)
- An Órgano that has never been classified — including a newly imported one — has a null
  `taxonomyNodeId` until it is assigned. (SPEC-0004 #18)
- Assigning to an unknown node, or assigning an unknown Órgano, is rejected and writes
  nothing.
- `ListOrganos` returns **every** stored Órgano exactly once with its name, active state and
  placement or null — nothing in the catalogue is unreachable through it, whether or not any
  node exists. (SPEC-0004 #8)
- `ListTaxonomyNodes` returns **every** node exactly once with its parent's id, or null for
  a root; with no node created it returns an empty list. (SPEC-0004 #9)
- Unit-tested against test doubles of the ports, covering the reassignment case, the
  cleared-placement case, and both reads with an empty taxonomy.
