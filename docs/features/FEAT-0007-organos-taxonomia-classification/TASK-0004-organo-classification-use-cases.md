---
feat: FEAT-0007
domain: backend
adrs: [0002]
status: done
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
    an Órgano rather than the taxonomy, so filing it under `domain.organo.taxonomia`
    alongside the term-scoped four would misplace it.

  The unknown-Órgano check is what the `OrganoRepository` by-id read from TASK-0001 exists
  for; scanning `findAllOrderByName()` to answer it would be the whole-table server work this
  feature avoids.
- `ClearOrganoTermo` — removes an Órgano's placement, returning it to unclassified. Rejects
  an unknown Órgano with the same exception `AssignOrganoToTermo` uses. Clearing an Órgano
  that is **already** unclassified is **not** an error: it is idempotent and writes nothing,
  because the caller's intent is already satisfied and an admin double-clicking a row should
  not see a failure.
- Assign and clear write one Órgano row and cannot reshape the tree, so they need no
  coordination with the taxonomy's own writes. An assign racing a `DeleteTermo` is left to
  the foreign key: the assign either commits before the delete and is cleared by it, or
  fails against the vanished term — a raced 500, accepted for how rarely this is written to.
- `ListOrganos` — every stored Órgano, each with its name, active state and its
  `termoId` or null. A straight `findAllOrderByName()`: no filtering, no paging, no grouping.
- `ListTermos` — every term, each with its name and its `parentId` or null.
  Likewise a straight `findAllOrderByName()`: **no tree is assembled here**, no descendant
  walk, and no term carries its children or its Órganos.
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
- `ListOrganos` returns `OrganoRepository.findAllOrderByName()` unchanged — the same elements
  in the same order, nothing filtered, grouped, sorted or re-shaped on the way through.
- `ListTermos` does the same for `TermoRepository.findAllOrderByName()`, returning an
  empty list when the repository holds no term.
- Unit-tested against Mockito doubles of the ports, covering the reassignment case, the
  cleared-placement case, the rejections, and the empty-repository case for `ListTermos` —
  the convention in `.claude/rules/backend/java-unit-test.md` and the shape every other
  FEAT-0007 use-case test already takes. A recording `FakeOrganoRepository` is *not* used:
  TASK-0001 deleted the one that existed, and it would earn nothing here, because placement
  is a single `termo_id` column and `updateTermo` overwrites it. Two ordered `updateTermo`
  calls and no third are therefore the whole of SPEC-0004 #17 — an Órgano has no way to hold
  two placements for a fake to catch. That an assignment is *durably* singular is proven
  where it is observable, against the database in TASK-0002 and over HTTP in TASK-0005.
