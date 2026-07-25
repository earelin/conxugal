---
feat: FEAT-0007
domain: backend
adrs: [0002]
status: todo
depends_on: [TASK-0001]
---

# Taxonomy management use cases

The four operations that build and reshape the tree, each a single-purpose domain class
over the ports from [TASK-0001](TASK-0001-taxonomy-node-domain-model-and-placement.md).
Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md): the rules live
in `domain`, not in a controller.

## Scope
- `CreateNode` — a node with a name, either at the root or under an existing parent;
  rejects an unknown parent.
- `RenameNode` — changes the name of an existing node; rejects an unknown node.
- `MoveNode` — re-parents a node, or moves it to the root. Enforces the **cycle guard**:
  the target parent may be neither the node itself nor any of its descendants. A rejected
  move writes nothing.
- `DeleteNode` — applies the R16 rules: **rejected while the node has child nodes**;
  otherwise deletes the node and returns the Órganos placed directly in it to the
  unclassified set. Deletes no Órgano.
- Distinct domain exceptions for the rejections (unknown node, cycle, node still has
  children), so the endpoints of
  [TASK-0005](TASK-0005-taxonomy-and-classification-rest-endpoints.md) can map each to its
  own status without inspecting messages.
- `DeleteNode` runs its delete and the placement clearing it triggers in **one
  transaction**, so a concurrent reader never sees an Órgano pointing at a node that is
  already gone.

## Acceptance criteria
- A node can be created at the root and under a parent, renamed, and moved to a different
  parent; nesting several levels deep works.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #14)
- Moving a node under **itself** is rejected and the taxonomy is unchanged.
  (SPEC-0004 #15)
- Moving a node under one of its own **descendants** — including a grandchild, not just a
  direct child — is rejected and the taxonomy is unchanged. (SPEC-0004 #15)
- Deleting a node that has child nodes is rejected; the node and its children remain.
  (SPEC-0004 #16)
- Deleting a node that has Órganos placed directly in it succeeds, those Órganos become
  unclassified, and every one of them still exists. (SPEC-0004 #16)
- Each rejection surfaces as its own exception type.
- Unit-tested against a test double of `TaxonomyNodeRepository` / `OrganoRepository`; the
  cycle guard is tested at depth, not only one level down.
