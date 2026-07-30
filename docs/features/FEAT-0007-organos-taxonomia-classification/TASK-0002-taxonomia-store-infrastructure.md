---
feat: FEAT-0007
domain: backend
adrs: [0002, 0008]
status: done
depends_on: [TASK-0001]
---

# Taxonomía store infrastructure: JDBC repositories

The driven adapters for the ports added in
[TASK-0001](TASK-0001-termo-domain-model-and-placement.md), against the schema that
task's migration already created. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md) and
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md);
JDBC/SQL stays entirely in `infrastructure`.

## Scope
- **No migration here** — the `termo` table and
  `organo_contratacion.termo_id` ship with TASK-0001, because widening the
  `OrganoDeContratacion` record breaks every existing Órgano query until the column exists.
  This task adds adapters only; if a column turns out to be missing, the fix belongs in
  TASK-0001's migration, not a second one.
- Micronaut Data JDBC implementation of `TermoRepository`: find all, find by id,
  insert, rename, re-parent, delete, the child-existence check, and the children-of-parent
  read (a null parent must return the roots, which is not the same query as a parent match).
  No recursive CTE and no subtree query — the endpoint serves the whole table and the client
  builds the tree.
- **Both whole-table reads order by name** under TASK-0001's Galician collation:
  `TermoRepository.findAllOrderByName()` and `OrganoRepository.findAllOrderByName()`. The
  feature's *API surface* promises this order to callers, so it is a contract, not a
  convenience.

  **Corrected against what TASK-0001 shipped.** This bullet originally called for `@Query`
  with an explicit `ORDER BY name COLLATE <the declared collation>`, on the reasoning that a
  derived method would give the ordering but not the collation. TASK-0001 then declared the
  collation **on the `name` columns themselves**, precisely so every derived `ORDER BY name`
  inherits it — and `TermoMigrationIntegrationTest` asserts exactly that against a bare
  `ORDER BY name`.
  So both reads are plain derived methods with no `@Query`, and the collation is still the
  whole point: the accented-name criterion below is what holds it.
- **No rule and no exception here.** The adapter does not inspect SQLSTATEs and does not
  translate constraint violations into domain exceptions: every refusal this feature makes
  is a use-case check in [TASK-0003](TASK-0003-taxonomia-management-use-cases.md), reading
  through the ports above. The schema's unique index and foreign keys stay as **integrity
  backstops** — a violation reaching the adapter means two admins wrote in the same instant,
  and it surfaces as a 500 rather than as one of the feature's problem types. The taxonomy
  is written to a handful of times in its life, so that window is accepted rather than
  closed with a translation layer and a lock; the feature's *Edge cases* records the trade.
- Placement operations on `JdbcOrganoRepository`: set an Órgano's term, clear it, clear every
  placement pointing at a given term, and read by id. **Expect to write no code for most of
  these** — both repositories are bare derived interfaces, so Micronaut Data generates the
  bodies from the port's method names, and it does so during *TASK-0001's* build, not this
  one. What is left here is the `@Query` operations that cannot derive (the children-of-parent
  read, and *clear every placement pointing at a term* if an update matched on a non-id
  column does not derive), and proving the rest against a real database.
  No `findByTermo` and no `findUnclassified` — `findAllOrderByName()` already carries
  `termo_id` on every row.

## Acceptance criteria
- Terms persist and reload with their edges intact: `findAllOrderByName` returns a root with a
  null `parentId` and a child carrying its parent's id, so several levels of nesting round-trip
  as a flat list a caller can rebuild the tree from.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #14)
- Both `findAllOrderByName` methods return rows **in name order**, from a fixture inserted in a
  deliberately different order, and accented Galician names land where a reader expects
  (`Ávila` beside `Avión`, not after `Zamora`). The accent case is the one that fails on a
  default-collation cluster, which is exactly the failure this ordering exists to prevent.
- Every term operation round-trips against the database, not just the whole-table read: a
  rename shows the new name and nothing else changed; a re-parent moves the term between
  parents and to the root (null); find-by-id returns the stored term and nothing for an
  unknown id; delete removes exactly one term. (SPEC-0004 #14)
- The child check answers true for a term with children and false for a leaf, and the
  children-of-parent read returns a parent's direct children — and, given a null parent, the
  roots. These are what `DeleteTermo`'s refusal and the sibling-name rule stand on, so a
  wrong answer here silently disables both.
- Setting an Órgano's term, then setting a different one, leaves exactly one placement on
  the row; clearing it leaves none. (SPEC-0004 #17)
- The schema still refuses what TASK-0001 built it to refuse — a delete blocked by children
  or by an uncleared placement, and a duplicate sibling name — which
  `TermoMigrationIntegrationTest` already asserts directly. This task neither translates nor
  re-tests those; it must simply not paper over them. (SPEC-0004 #16)
- The adapters satisfy the domain port contracts, integration-tested against PostgreSQL
  (Testcontainers).
