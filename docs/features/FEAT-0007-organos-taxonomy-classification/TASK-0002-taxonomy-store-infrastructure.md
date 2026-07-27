---
feat: FEAT-0007
domain: backend
adrs: [0002, 0008]
status: todo
depends_on: [TASK-0001]
---

# Taxonomy store infrastructure: JDBC repositories

The driven adapters for the ports added in
[TASK-0001](TASK-0001-taxonomy-node-domain-model-and-placement.md), against the schema that
task's migration already created. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md) and
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md);
JDBC/SQL stays entirely in `infrastructure`.

## Scope
- **No migration here** — the `taxonomy_node` table and
  `organo_contratacion.taxonomy_node_id` ship with TASK-0001, because widening the
  `OrganoDeContratacion` record breaks every existing Órgano query until the column exists.
  This task adds adapters only; if a column turns out to be missing, the fix belongs in
  TASK-0001's migration, not a second one.
- Micronaut Data JDBC implementation of `TaxonomyNodeRepository`: find all, find by id,
  insert, rename, re-parent, delete, the child-existence check, and the children-of-parent
  read (a null parent must return the roots, which is not the same query as a parent match).
  No recursive CTE and no subtree query — the endpoint serves the whole table and the client
  builds the tree.
- `lockTaxonomy` has no derivable form: implement it as
  `SELECT pg_advisory_xact_lock(<fixed key>)` via `@Query`, pinning how the result is
  mapped — a `void`/`Void` return over a `SELECT` is the wrinkle to settle here rather than
  at the call site. Record the chosen key where the next person will find it.
  **It only serialises inside a transaction**, which
  [TASK-0003](TASK-0003-taxonomy-management-use-cases.md)'s use cases open: called without
  one, it is acquired and released within its own statement and quietly does nothing.
- **Translate the two constraint violations into domain exceptions**, so a raced refusal
  reaches the same problem type as a checked one instead of a 500: the unique-index
  violation on `(parent_id, lower(name))` becomes the duplicate-sibling-name exception, and
  the foreign-key violation on `taxonomy_node_id` becomes node-not-found. Match on
  SQLSTATE/constraint name, not on message text. This is the layer that keeps SQLSTATE
  knowledge out of `domain` and out of the controllers.
- Placement operations on `JdbcOrganoRepository`: set an Órgano's node, clear it, clear every
  placement pointing at a given node, and read by id. **Expect to write no code for most of
  these** — both repositories are bare derived interfaces, so Micronaut Data generates the
  bodies from the port's method names, and it does so during *TASK-0001's* build, not this
  one. What is left here is the `@Query` operations that cannot derive (`lockTaxonomy`, and
  *clear every placement pointing at a node* if an update matched on a non-id column does
  not derive), the translation layer above, and proving the rest against a real database.
  No `findByNode` and no `findUnclassified` — `findAll()` already carries
  `taxonomy_node_id` on every row.

## Acceptance criteria
- Nodes persist and reload with their edges intact: `findAll` returns a root with a null
  `parentId` and a child carrying its parent's id, so several levels of nesting round-trip
  as a flat list a caller can rebuild the tree from.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #14)
- Every node operation round-trips against the database, not just `findAll`: a rename shows
  the new name and nothing else changed; a re-parent moves the node between parents and to
  the root (null); find-by-id returns the stored node and nothing for an unknown id; delete
  removes exactly one node. (SPEC-0004 #14)
- The child check answers true for a node with children and false for a leaf, and the
  children-of-parent read returns a parent's direct children — and, given a null parent, the
  roots. These are what `DeleteNode`'s refusal and the sibling-name rule stand on, so a
  wrong answer here silently disables both.
- Setting an Órgano's node, then setting a different one, leaves exactly one placement on
  the row; clearing it leaves none. (SPEC-0004 #17)
- `lockTaxonomy` actually serialises: two concurrent transactions that both call it are
  ordered, the second proceeding only once the first commits or rolls back, and the lock is
  released without an explicit unlock. Proven with two real connections — a single-threaded
  test cannot tell a working lock from a no-op.
- Inserting a duplicate sibling name raises the **duplicate-sibling-name domain exception**,
  not a raw `DataAccessException`; deleting a node that still has placements raises
  **node-not-found**. Without this the raced paths return 500 where the contract promises
  409 and 404 — and the happy paths would still pass every other criterion here.
- Deleting a node whose placements were **not** cleared first is refused by the database —
  the foreign key raises rather than cascading or nulling. This is the criterion that
  distinguishes TASK-0001's bare foreign key from an `ON DELETE CASCADE` or `SET NULL` one;
  the clear-then-delete happy path is `DeleteNode`'s, in
  [TASK-0003](TASK-0003-taxonomy-management-use-cases.md). (SPEC-0004 #16)
- The adapters satisfy the domain port contracts, integration-tested against PostgreSQL
  (Testcontainers).
