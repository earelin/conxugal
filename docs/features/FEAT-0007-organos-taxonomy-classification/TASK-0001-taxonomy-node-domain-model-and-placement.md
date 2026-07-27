---
feat: FEAT-0007
domain: backend
adrs: [0002, 0008]
status: todo
depends_on: []
---

# Taxonomy node domain model + Órgano placement + schema migration

The `TaxonomyNode` aggregate and its repository port, the placement field that puts an
Órgano in a node, and the migration that gives both their columns. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md) (the domain owns the model
and declares the port) and
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md)
(the aggregate carries its own mapping annotations and maps 1:1 to one table). Extends the
`OrganoDeContratacion` aggregate and `OrganoRepository` port delivered by
[FEAT-0006 TASK-0001](../FEAT-0006-organos-catalogue-import/TASK-0001-organo-domain-model-and-repository-port.md).

**Why the migration ships here and not in
[TASK-0002](TASK-0002-taxonomy-store-infrastructure.md).** Under ADR-0008 the aggregate
*is* the mapping: `JdbcOrganoRepository` is a bare derived interface, so the moment
`taxonomyNodeId` joins the `OrganoDeContratacion` record, the generated SQL for `findAll`,
`findAllBySourceKeyIn` and `insert` names a column the schema does not have. (`update` and
`updateActive` take explicit parameters and are unaffected.) FEAT-0006's repository
integration tests then go red on a task that touches no infrastructure code — and because
neither `integrationTest` suite is part of `check`, `./gradlew build` would stay green
while CI failed.

Model and column are one cohesive change, and the placement's foreign key needs the
`taxonomy_node` table to exist, so both tables land in one migration. Note this **reverses
FEAT-0006's own pairing**, where the migration shipped alongside the JDBC repository in its
TASK-0002; the reversal is deliberate, and a reader arriving from FEAT-0006 should not read
it as an inconsistency. The reverse split — migration first, model second — would also land
green twice, so this is a cohesion argument rather than the only workable order.

## Scope
- `TaxonomyNode` record in `gal.conxugal.domain.taxonomy`: system-assigned `UUID id`
  (`@GeneratedValue`, null until the database assigns it), `String name`, and a
  **nullable** `UUID parentId` — a null parent is a root, which is what makes the taxonomy
  many-rooted and arbitrarily deep. The parent is held as the parent's **id**, not a nested
  node, keeping the 1:1 single-table mapping of ADR-0008.
- `TaxonomyNodeRepository` port: **find all** — the read endpoint serves this list verbatim,
  so there is no by-parent or subtree query — plus find by id, insert, rename, re-parent,
  delete, an `existsByParentId`-style child check the delete rule needs, a
  **children-of-parent read** the sibling-name rule needs (roots included, so a null parent
  is a legal argument), and `lockTaxonomy` — the serialising lock the feature's *Edge cases*
  require, declared in the domain's own terms so the advisory-lock mechanism stays in
  `infrastructure` (ADR-0002).
- Placement on the Órgano: a **nullable** `UUID taxonomyNodeId` on `OrganoDeContratacion`,
  and the `OrganoRepository` operations that write it — set the node, clear it, and clear
  every placement pointing at a given node (what `DeleteNode` needs). Placement is exactly
  one node or none; there is no collection.
- A **by-id read** on `OrganoRepository` (`findById`/`existsById`). The port has none today
  — reconciliation matches on `sourceKey` — and
  [TASK-0004](TASK-0004-organo-classification-use-cases.md) must reject an unknown Órgano,
  which without it means a `findAll()` scan per assign: exactly the whole-table server work
  this feature is built to avoid.
- **No filtered reads.** There is deliberately no `findByNode` and no `findUnclassified`:
  `GET /api/organos` returns `findAll()` with each row's `taxonomyNodeId`, and every
  by-node or unclassified view is a client-side filter over that list. Adding either query
  here would ship port surface with no caller.
- Keep the placement out of the reconciliation write paths: `update` and `updateActive`
  must go on writing name/active only, so an import cannot disturb a placement.
- **Migration** `V9__create_taxonomy_node_and_organo_placement.sql` under
  `infrastructure/src/main/resources/db/migration/`, creating the `taxonomy_node` table —
  UUID primary key defaulting to `uuidv7()` as `organo_contratacion` does,
  `name VARCHAR(255) NOT NULL` (matching the `@Size` the request records enforce, and the
  `organo_contratacion.name` precedent), and a nullable self-referencing `parent_id` with a
  foreign key back to `taxonomy_node` — and adding the nullable `taxonomy_node_id` to
  `organo_contratacion`
  with a foreign key to `taxonomy_node`. Both tables in one migration: the placement's
  foreign key needs the node table to exist already. **V9, not V8** — V8 is taken, and V4
  is reserved by `db/migration-local/V4__seed_demo_user.sql`, so a free-looking number in
  `db/migration/` is not necessarily free.
- The same migration adds a **unique index on `(parent_id, lower(name))` with
  `NULLS NOT DISTINCT`**, enforcing the feature's sibling-name rule in the one place a
  concurrent create cannot slip past. `NULLS NOT DISTINCT` is what extends it to the roots:
  under the default, every null `parent_id` is distinct and two identically-named roots
  would be accepted. `lower(name)` makes it case-insensitive, matching the rule as stated.
- The self-referencing `taxonomy_node.parent_id` foreign key likewise carries **no
  `ON DELETE` action**. `CASCADE` here would let one delete remove a whole subtree, quietly
  defeating the R16 rule that a delete is *rejected* while the node has children — the
  refusal would never get the chance to fire.
- The foreign key on `organo_contratacion.taxonomy_node_id` carries **no `ON DELETE`
  action** — not `SET NULL`, and never `CASCADE`. `DeleteNode` clears the placements itself
  in the same transaction, and the bare foreign key is the backstop that turns a skipped
  clearing into a loud constraint violation rather than a silent mass-unclassify. This is
  settled in the feature's *Placement and classification* section; no later task revisits
  it.
- **No JDBC repository code here.** The migration is schema only; the
  `JdbcTaxonomyNodeRepository` and the placement operations on `JdbcOrganoRepository` are
  [TASK-0002](TASK-0002-taxonomy-store-infrastructure.md).
- Update **`FakeOrganoRepository`** (`domain/src/test/.../organo/`) for the widened record
  and port. It implements `OrganoRepository`, so it stops compiling here, and its
  `update`/`updateActive` rebuild the record component-by-component: the quickest fix is to
  pass `null` for the new component, which would make the fake **silently drop placements
  on every reconciliation write** — the exact invariant SPEC-0004 #5 protects, defeated in
  the test double that is supposed to prove it. It must preserve the existing placement
  instead, and grow the by-id read.

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
  rename, re-parent, delete, check for children, read a parent's children, lock the
  taxonomy, set/clear an Órgano's node, clear the placements pointing at a node, and read an
  Órgano by id — and nothing they do not; no infrastructure type leaks into its signatures.
- The migration applies cleanly on top of the existing schema and creates both the
  `taxonomy_node` table (with its self-referencing parent foreign key) and
  `organo_contratacion.taxonomy_node_id` (nullable, foreign-keyed, no `ON DELETE` action).
- The sibling-name index does what it claims, asserted directly against the database:
  two same-named children of one parent are refused, **two same-named roots are refused**
  (the `NULLS NOT DISTINCT` case, which a default index would silently allow), a
  case-only difference is refused, and the same name under two different parents is
  accepted. This is the feature's only race-proof guard on the rule, and it is one keyword
  away from not working.
- **The task lands green.** FEAT-0006's existing `JdbcOrganoRepository` integration tests
  still pass unchanged against the migrated schema — the widened record round-trips with
  `taxonomyNodeId` null — which is the whole reason the migration is in this task.
- `findAll` reports each Órgano's `taxonomyNodeId`: the node's id for a placed one, null for
  an unplaced one, including a freshly inserted Órgano. (SPEC-0004 #8, #18)
- The reconciliation write paths leave the placement alone: `update` and `updateActive`
  against a **placed** Órgano change name/active and leave `taxonomy_node_id` intact — an
  import can neither move nor drop a placement, and an Órgano going inactive keeps its node.
  Verified in the repository integration suite and in `FakeOrganoRepository`'s own tests,
  since a fake that drops the placement would make every downstream use-case test lie.
  (SPEC-0004 #5, #6)
- The domain model is unit-tested at the record level (construction, null-parent root,
  null-placement unclassified) without a database; the migration and the two criteria above
  are covered by the existing Testcontainers integration suite, which applies it on startup.
