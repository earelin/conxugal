---
feat: FEAT-0007
domain: backend
adrs: [0002]
status: todo
depends_on: [TASK-0001, TASK-0003]
---

# Órgano classification use cases + catalogue reads

Placing an Órgano in a node, taking it out, and the two whole-table reads the
authenticated endpoints serve. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md), over the ports from
[TASK-0001](TASK-0001-taxonomy-node-domain-model-and-placement.md).

## Scope
- `AssignOrganoToNode` — sets an Órgano's placement to a node, **replacing** any current
  one; rejects an unknown Órgano or an unknown node. Two **different** exceptions, because
  the feature's failure contract gives them different problem types:
  - the unknown-**node** exception is
    [TASK-0003](TASK-0003-taxonomy-management-use-cases.md)'s — reuse it rather than
    declaring a second type, or TASK-0006 has two exceptions mapping to one problem type;
  - the unknown-**Órgano** exception is **declared by this task**, in `domain.organo`. It is
    the fifth type in the feature's failure contract and no other task owns it; it is about
    an Órgano, so filing it under `domain.taxonomy` for tidiness would misplace it.

  The unknown-Órgano check is what the `OrganoRepository` by-id read from TASK-0001 exists
  for; scanning `findAll()` to answer it would be the whole-table server work this feature
  avoids.
- `ClearOrganoNode` — removes an Órgano's placement, returning it to unclassified. Rejects
  an unknown Órgano with the same exception `AssignOrganoToNode` uses. Clearing an Órgano
  that is **already** unclassified is **not** an error: it is idempotent and writes nothing,
  because the caller's intent is already satisfied and an admin double-clicking a row should
  not see a failure.
- Assign and clear take **no taxonomy lock** — they write one Órgano row and cannot reshape
  the tree. An assign racing a `DeleteNode` is settled by the lock TASK-0003 holds and the
  foreign key: the assign either commits first and is cleared by the delete, or fails
  against the vanished node.
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
  [TASK-0005](TASK-0005-taxonomy-read-endpoints.md).
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
- Assigning to an unknown node, or assigning an unknown Órgano, is rejected and writes
  nothing; clearing an unknown Órgano is rejected likewise, while clearing an
  already-unclassified one succeeds and writes nothing.
- An unknown node and an unknown Órgano raise **distinct** exception types — TASK-0003's for
  the node, this task's for the Órgano — so TASK-0006 can return two different 404 problem
  types. No second unknown-**node** type is declared.
- `ListOrganos` returns `OrganoRepository.findAll()` unchanged — the same elements in the
  same order, nothing filtered, grouped, sorted or re-shaped on the way through.
- `ListTaxonomyNodes` does the same for `TaxonomyNodeRepository.findAll()`, returning an
  empty list when the repository holds no node.
- Unit-tested against test doubles of the ports, covering the reassignment case, the
  cleared-placement case, the rejections, and the empty-repository case for
  `ListTaxonomyNodes`. The placement assertions run against the **recording
  `FakeOrganoRepository`**, not a stub: a mock that returns whatever the test told it to
  cannot show that a reassignment left *one* placement rather than two, which is the whole
  claim of SPEC-0004 #17.
