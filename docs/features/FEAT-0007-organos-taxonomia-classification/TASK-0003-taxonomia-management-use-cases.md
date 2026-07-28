---
feat: FEAT-0007
domain: backend
adrs: [0002]
status: todo
depends_on: [TASK-0001]
---

# Taxonomía management use cases

The four operations that build and reshape the tree, each a single-purpose domain class
over the ports from [TASK-0001](TASK-0001-termo-domain-model-and-placement.md).
Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md): the rules live
in `domain`, not in a controller.

## Scope
- `CreateTermo` — a term with a name, either at the root or under an existing parent;
  rejects an unknown parent.
- `RenameTermo` — changes the name of an existing term; rejects an unknown term.
- `MoveTermo` — re-parents a term, or moves it to the root. Rejects an unknown term **and an
  unknown target parent** — without the second check the cycle guard walks up from a parent
  that does not exist, and what the feature promises as a 404 surfaces as a 500. Enforces
  the **cycle guard**: the target parent may be neither the term itself nor any of its
  descendants. A rejected move writes nothing.
- `DeleteTermo` — applies the R16 rules: **rejected while the term has child terms**;
  otherwise deletes the term and returns the Órganos placed directly in it to the
  unclassified set. Deletes no Órgano.
- The **sibling-name rule** from the feature's *Taxonomía as a tree*: a name is required,
  non-blank once trimmed, stored trimmed, and unique case-insensitively among its siblings
  (roots being siblings of each other). It binds `CreateTermo`, `RenameTermo` and the
  `MoveTermo` that lands a term beside a new set of siblings. Length and blankness are
  rejected at the edge by the request record; the sibling comparison lives here, where the
  repository read it needs is available, and TASK-0001's unique index is what makes it
  race-proof — this check exists to produce a civil refusal, not to be the only guard.
- **This task owns the feature's four term-scoped rejection exceptions** — unknown term,
  cycle, term still has children, duplicate sibling name — as distinct domain types in
  `domain.organo`, so [TASK-0006](TASK-0006-taxonomia-admin-endpoints.md) can map each to
  its own status and problem type without inspecting messages.
  [TASK-0004](TASK-0004-organo-classification-use-cases.md) reuses the unknown-**term** type
  rather than declaring a second one; it is listed here so two tasks picked up in parallel
  do not each invent one. The fifth type in the feature's failure contract,
  **unknown Órgano, is TASK-0004's** and lives in `domain.organo` — it is about an Órgano,
  not the taxonomy.
- **Tree-shape mutations serialise.** `MoveTermo` and `DeleteTermo` carry `@Transactional`
  (`io.micronaut.transaction.annotation`) on the use-case method — the same boundary
  `SetUserEnabled` and `CreateUser` already use — and call `lockTaxonomia` before their first
  read, holding it through the write. **The annotation is not optional decoration**: the
  lock is transaction-scoped, so without an ambient transaction it is released inside its
  own statement and serialises nothing, while every single-threaded test still passes. For
  `MoveTermo` this is what
  makes the cycle guard sound at all — a check-then-write over a tree another admin is
  reshaping is not a guard (see the feature's *Edge cases*). For `DeleteTermo` the same
  transaction also covers the placement clearing, so a concurrent reader never sees an
  Órgano pointing at a term that is already gone. `CreateTermo` and `RenameTermo` do not
  reshape the tree and take no lock: the only thing they can race on is the sibling-name
  rule, and the unique index settles that in the database rather than by making every
  create wait.

## Acceptance criteria
- A term can be created at the root and under a parent, renamed, and moved to a different
  parent; nesting several levels deep works.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #14)
- Moving a term under **itself** is rejected and the taxonomy is unchanged.
  (SPEC-0004 #15)
- Moving a term under one of its own **descendants** — including a grandchild, not just a
  direct child — is rejected and the taxonomy is unchanged. (SPEC-0004 #15)
- Deleting a term that has child terms is rejected; the term and its children remain.
  (SPEC-0004 #16)
- Deleting a term that has Órganos placed directly in it succeeds, those Órganos become
  unclassified, and every one of them still exists. (SPEC-0004 #16)
- Creating a term under an unknown parent, renaming an unknown term, and moving a term
  **onto an unknown parent** are each rejected and write nothing — the last is the path that
  would otherwise walk a non-existent ancestry and fail as a 500.
- A create, a rename, or a move that would put two same-named siblings under one parent is
  rejected — including two roots and including a case-only difference — while the same name
  under a different parent is accepted. (SPEC-0004 #14)
- Each rejection surfaces as its own exception type, and unknown-term is a single type
  shared with [TASK-0004](TASK-0004-organo-classification-use-cases.md), not a second one.
- `MoveTermo` and `DeleteTermo` take the taxonomy lock before their first read; `CreateTermo`
  and `RenameTermo` do not. Provable against the test double by recording the call order —
  a guard that reads before locking is the bug this exists to prevent, and it looks
  identical to a correct one in a single-threaded test otherwise.
- **The serialisation is proven for real**, not only by call order: an integration test
  drives the injected `MoveTermo` from concurrent threads against a real database and shows
  that two moves which would jointly create a cycle cannot both commit, following
  `SetUserEnabledConcurrencyIntegrationTest`. This is the only criterion in the feature that
  can fail when `@Transactional` is missing — every other test passes with the lock doing
  nothing at all.
- Unit-tested against a test double of `TermoRepository` / `OrganoRepository`; the
  cycle guard is tested at depth, not only one level down.
