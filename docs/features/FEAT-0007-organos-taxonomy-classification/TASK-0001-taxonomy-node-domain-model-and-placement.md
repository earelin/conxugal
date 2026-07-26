---
feat: FEAT-0007
domain: backend
adrs: [0002, 0008]
status: todo
depends_on: []
---

# Taxonomy node domain model + Órgano placement

The `TaxonomyNode` aggregate and its repository port, plus the placement field that puts an
Órgano in a node. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md) (the domain owns the model
and declares the port) and
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md)
(the aggregate carries its own mapping annotations and maps 1:1 to one table). Extends the
`OrganoDeContratacion` aggregate and `OrganoRepository` port delivered by
[FEAT-0006 TASK-0001](../FEAT-0006-organos-catalogue-import/TASK-0001-organo-domain-model-and-repository-port.md).

## Scope
- `TaxonomyNode` record in `gal.conxugal.domain.taxonomy`: system-assigned `UUID id`
  (`@GeneratedValue`, null until the database assigns it), `String name`, and a
  **nullable** `UUID parentId` — a null parent is a root, which is what makes the taxonomy
  many-rooted and arbitrarily deep. The parent is held as the parent's **id**, not a nested
  node, keeping the 1:1 single-table mapping of ADR-0008.
- `TaxonomyNodeRepository` port: **find all** — the read endpoint serves this list verbatim,
  so there is no by-parent or subtree query — plus find by id, insert, rename, re-parent,
  delete, and an `existsByParentId`-style child check the delete rule needs.
- Placement on the Órgano: a **nullable** `UUID taxonomyNodeId` on `OrganoDeContratacion`,
  and the `OrganoRepository` operations that write it — set the node, clear it, and clear
  every placement pointing at a given node (what `DeleteNode` needs). Placement is exactly
  one node or none; there is no collection.
- **No filtered reads.** There is deliberately no `findByNode` and no `findUnclassified`:
  `GET /api/organos` returns `findAll()` with each row's `taxonomyNodeId`, and every
  by-node or unclassified view is a client-side filter over that list. Adding either query
  here would ship port surface with no caller.
- Keep the placement out of the reconciliation write paths: `update` and `updateActive`
  must go on writing name/active only, so an import cannot disturb a placement.

## Acceptance criteria
- A `TaxonomyNode` can be constructed as a root (null parent) or as a child of another
  node, and nothing in the model caps the nesting depth.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #14)
- A node holds at most one parent by construction — the model offers no way to give it a
  second. (SPEC-0004 #15)
- An `OrganoDeContratacion` carries either one taxonomy node id or none; the type admits no
  second placement. (SPEC-0004 #17)
- An Órgano built without a placement is unclassified, and that is the state a newly
  imported one starts in. (SPEC-0004 #18)
- The port exposes every operation the later use cases need — find all, find by id, insert,
  rename, re-parent, delete, check for children, set/clear an Órgano's node, clear the
  placements pointing at a node — and nothing they do not; no infrastructure type leaks into
  its signatures.
- Unit-tested at the record level (construction, null-parent root, null-placement
  unclassified); no database involved.
