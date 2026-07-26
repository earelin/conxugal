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
- Deleting a node never deletes an Órgano row — after clearing the placements pointing at a
  node and deleting it through the adapters, the Órganos that pointed at it are still stored
  and unclassified. This exercises TASK-0001's non-cascading foreign key from the adapter
  side. (SPEC-0004 #16)
- The `update` / `updateActive` reconciliation paths from FEAT-0006 leave
  `taxonomy_node_id` untouched, verified against a placed Órgano. (SPEC-0004 #5)
- The adapters satisfy the domain port contracts, integration-tested against PostgreSQL
  (Testcontainers).
