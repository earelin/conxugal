---
feat: FEAT-0007
domain: backend
adrs: [0002]
status: done
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
  repository read it needs is available. TASK-0001's unique index stays underneath it as an
  integrity backstop, but this check is what produces the refusal a caller sees.
- **This task owns the feature's four term-scoped rejection exceptions** — unknown term,
  cycle, term still has children, duplicate sibling name — as distinct domain types in
  `domain.organo.taxonomia`, so [TASK-0006](TASK-0006-taxonomia-admin-endpoints.md) can map
  each to its own status and problem type without inspecting messages.
  [TASK-0004](TASK-0004-organo-classification-use-cases.md) reuses the unknown-**term** type
  rather than declaring a second one; it is listed here so two tasks picked up in parallel
  do not each invent one. The fifth type in the feature's failure contract,
  **unknown Órgano, is TASK-0004's** and lives in `domain.organo` — it is about an Órgano,
  not the taxonomy. Every one of them is raised by a check in this task; none comes from
  translating a database error.
- **`DeleteTermo` is one statement, so it needs no transaction.** This task carries the
  migration that alters `organo_contratacion_termo_id_fkey` to `ON DELETE SET NULL`, so the
  database returns the placed Órganos to unclassified as part of the delete itself. That
  reverses the feature's earlier call to clear them in domain code over a `NO ACTION` key —
  see *Placement and classification* for why — and with it go `@Transactional`, the
  `OrganoRepository.clearPlacementsByTermo` port operation that existed only to serve it,
  its `@Query` in `JdbcOrganoRepository`, and the test that provoked a failure between the
  two writes. `CASCADE` stays forbidden: it would delete Órganos, which R16 prohibits.
- **The child-term refusal stays in the use case.** The parent key on `termo.parent_id`
  keeps its `NO ACTION` — cascading there would silently remove a whole subtree — so
  `DeleteTermo` asks `existsByParentId` and refuses, with the key behind it as a backstop.
- **No lock, and the check-then-write window is accepted.** Every rule here reads and then
  writes, so two admins acting in the same instant could in principle both pass. The
  taxonomy is an `ADMIN`-only table of a few dozen rows edited a handful of times in its
  life; serialising every mutation to close that window costs more machinery than the risk
  warrants (see the feature's *Edge cases*). The schema's unique index and foreign keys
  remain as integrity backstops, and a violation reaching the adapter surfaces as a 500 —
  accepted, not translated.

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
- Deleting a term through `DeleteTermo` unclassifies the Órganos placed in it, leaves every
  one of them in the catalogue, and disturbs no other term's placements — proven against a
  real database, since the clearing is the foreign key's rather than the use case's.
- Unit-tested against a test double of `TermoRepository`; the cycle guard is tested at
  depth, not only one level down, and terminates on an ancestry that already loops.
