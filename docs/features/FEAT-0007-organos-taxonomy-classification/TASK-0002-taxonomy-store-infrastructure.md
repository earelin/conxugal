---
feat: FEAT-0007
domain: backend
adrs: [0002, 0008]
status: todo
depends_on: [TASK-0001]
---

# Taxonomy store infrastructure: migration + JDBC repositories

The schema and driven adapters for the ports added in
[TASK-0001](TASK-0001-taxonomy-node-domain-model-and-placement.md). Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md) and
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md);
JDBC/SQL stays entirely in `infrastructure`.

## Scope
- Migration creating the `taxonomy_node` table: UUID primary key, `name` (`NOT NULL`), and
  a nullable self-referencing `parent_id` with a foreign key back to `taxonomy_node`.
- Same migration adds a nullable `taxonomy_node_id` to `organo_contratacion`, with a
  foreign key to `taxonomy_node` — the placement lives on the Órgano row that FEAT-0006
  reconciles in place, which is what keeps it across imports.
- The foreign key on `organo_contratacion.taxonomy_node_id` is **not** `ON DELETE CASCADE`:
  deleting a node must return its Órganos to unclassified, never delete them. Whether the
  clearing is done by `ON DELETE SET NULL` or by the delete use case's own write is the
  implementation's call, but the outcome is fixed.
- Micronaut Data JDBC implementation of `TaxonomyNodeRepository`: find all, find by id,
  insert, rename, re-parent, delete, and the child-existence check. No recursive CTE and no
  subtree query — the endpoint serves the whole table and the client builds the tree.
- Placement operations on `JdbcOrganoRepository`: set an Órgano's node, clear it, and clear
  every placement pointing at a given node. No `findByNode` and no `findUnclassified` —
  `findAll()` already carries `taxonomy_node_id` on every row.

## Acceptance criteria
- Nodes persist and reload with their edges intact: `findAll` returns a root with a null
  `parentId` and a child carrying its parent's id, so several levels of nesting round-trip
  as a flat list a caller can rebuild the tree from.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #14)
- Setting an Órgano's node, then setting a different one, leaves exactly one placement on
  the row; clearing it leaves none. (SPEC-0004 #17)
- `findAll` returns each Órgano's `taxonomyNodeId` — the node's id for a placed one, null
  for an unplaced one, including a freshly inserted Órgano. This is a port-contract
  guarantee; the user-visible catalogue view it feeds (SPEC-0004 #8, #18) is proven over
  HTTP in [TASK-0005](TASK-0005-taxonomy-and-classification-rest-endpoints.md) and on the
  screen in [TASK-0006](TASK-0006-taxonomy-admin-ui.md).
- Deleting a node never deletes an Órgano row — after the delete, the Órganos that pointed
  at it are still stored and unclassified. (SPEC-0004 #16)
- The `update` / `updateActive` reconciliation paths from FEAT-0006 leave
  `taxonomy_node_id` untouched, verified against a placed Órgano. (SPEC-0004 #5)
- The adapters satisfy the domain port contracts, integration-tested against PostgreSQL
  (Testcontainers).
