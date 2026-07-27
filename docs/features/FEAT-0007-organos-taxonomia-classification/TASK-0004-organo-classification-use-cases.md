---
feat: FEAT-0007
domain: backend
adrs: [0002]
status: todo
depends_on: [TASK-0001, TASK-0003]
---

# Órgano classification use cases + catalogue reads

Placing an Órgano in a term, taking it out, and the two whole-table reads the
authenticated endpoints serve. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md), over the ports from
[TASK-0001](TASK-0001-termo-domain-model-and-placement.md).

## Scope
- `AssignOrganoToTermo` — sets an Órgano's placement to a term, **replacing** any current
  one; rejects an unknown Órgano or an unknown term. Two **different** exceptions, because
  the feature's failure contract gives them different problem types:
  - the unknown-**term** exception is
    [TASK-0003](TASK-0003-taxonomia-management-use-cases.md)'s — reuse it rather than
    declaring a second type, or TASK-0006 has two exceptions mapping to one problem type;
  - the unknown-**Órgano** exception is **declared by this task**, in `domain.organo`. It is
    the fifth type in the feature's failure contract and no other task owns it; it is about
    an Órgano, so filing it under `domain.organo` for tidiness would misplace it.

  The unknown-Órgano check is what the `OrganoRepository` by-id read from TASK-0001 exists
  for; scanning `findAll()` to answer it would be the whole-table server work this feature
  avoids.
- `ClearOrganoTermo` — removes an Órgano's placement, returning it to unclassified. Rejects
  an unknown Órgano with the same exception `AssignOrganoToTermo` uses. Clearing an Órgano
  that is **already** unclassified is **not** an error: it is idempotent and writes nothing,
  because the caller's intent is already satisfied and an admin double-clicking a row should
  not see a failure.
- Assign and clear take **no taxonomy lock** — they write one Órgano row and cannot reshape
  the tree. An assign racing a `DeleteTermo` is settled by the lock TASK-0003 holds and the
  foreign key: the assign either commits first and is cleared by the delete, or fails
  against the vanished term.
- `ListOrganos` — every stored Órgano, each with its name, active state and its
  `termoId` or null. A straight `findAll()`: no filtering, no paging, no grouping.
- `ListTermos` — every term, each with its name and its `parentId` or null.
  Likewise a straight `findAll()`: **no tree is assembled here**, no descendant walk, and no
  term carries its children or its Órganos.
- Both reads mirror the `ListUsers` precedent in `gal.conxugal.domain.user` — a thin,
  single-purpose use case over the port, so the controller depends on the domain rather than
  on a repository. Being pass-throughs, their unit tests can only prove they pass through;
  the substantive "every Órgano, exactly once, with its placement" guarantee (SPEC-0004 #8)
  is proven where it is observable — against the database in
  [TASK-0002](TASK-0002-taxonomia-store-infrastructure.md) and over HTTP in
  [TASK-0005](TASK-0005-taxonomia-read-endpoints.md).
- **No unclassified concept in the backend.** There is no `GetUnclassifiedOrganos` and no
  unclassified field in any result: an Órgano with a null `termoId` is unclassified,
  and callers filter for it. This is what the two-endpoint split buys — R8 and R18 are
  satisfied by the placement travelling on every Órgano, not by the server partitioning the
  catalogue.

## Acceptance criteria
- Assigning an Órgano to a term, then to a different term, leaves it placed in only the
  second; it is never in two at once.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #17)
- Clearing an assignment leaves the Órgano in no term — `ListOrganos` then reports a null
  `termoId` for it. (SPEC-0004 #17, #18)
- Assigning to an unknown term, or assigning an unknown Órgano, is rejected and writes
  nothing; clearing an unknown Órgano is rejected likewise, while clearing an
  already-unclassified one succeeds and writes nothing.
- An unknown term and an unknown Órgano raise **distinct** exception types — TASK-0003's for
  the term, this task's for the Órgano — so TASK-0006 can return two different 404 problem
  types. No second unknown-**term** type is declared.
- `ListOrganos` returns `OrganoRepository.findAll()` unchanged — the same elements in the
  same order, nothing filtered, grouped, sorted or re-shaped on the way through.
- `ListTermos` does the same for `TermoRepository.findAll()`, returning an
  empty list when the repository holds no term.
- Unit-tested against test doubles of the ports, covering the reassignment case, the
  cleared-placement case, the rejections, and the empty-repository case for
  `ListTermos`. The placement assertions run against the **recording
  `FakeOrganoRepository`**, not a stub: a mock that returns whatever the test told it to
  cannot show that a reassignment left *one* placement rather than two, which is the whole
  claim of SPEC-0004 #17.
