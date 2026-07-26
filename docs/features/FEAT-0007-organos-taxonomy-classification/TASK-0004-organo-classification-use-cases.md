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
  on a repository. Being pass-throughs, their unit tests can only prove they pass through;
  the substantive "every Órgano, exactly once, with its placement" guarantee (SPEC-0004 #8)
  is proven where it is observable — against the database in
  [TASK-0002](TASK-0002-taxonomy-store-infrastructure.md) and over HTTP in
  [TASK-0005](TASK-0005-taxonomy-and-classification-rest-endpoints.md).
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
- `ListOrganos` returns `OrganoRepository.findAll()` unchanged — the same elements in the
  same order, nothing filtered, grouped, sorted or re-shaped on the way through.
- `ListTaxonomyNodes` does the same for `TaxonomyNodeRepository.findAll()`, returning an
  empty list when the repository holds no node.
- Unit-tested against test doubles of the ports, covering the reassignment case, the
  cleared-placement case, the rejections, and the empty-repository case for
  `ListTaxonomyNodes`.
