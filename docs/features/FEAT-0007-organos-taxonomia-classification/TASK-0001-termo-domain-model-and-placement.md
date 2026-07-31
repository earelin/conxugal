---
feat: FEAT-0007
domain: backend
adrs: [0002, 0008]
status: done
depends_on: []
---

# Term domain model + Órgano placement + schema migration

The `Termo` aggregate and its repository port, the placement field that puts an
Órgano in a term, and the migration that gives both their columns. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md) (the domain owns the model
and declares the port) and
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md)
(the aggregate carries its own mapping annotations and maps 1:1 to one table). Extends the
`OrganoDeContratacion` aggregate and `OrganoRepository` port delivered by
[FEAT-0006 TASK-0001](../FEAT-0006-organos-catalogue-import/TASK-0001-organo-domain-model-and-repository-port.md).

**Why the migration ships here and not in
[TASK-0002](TASK-0002-taxonomia-store-infrastructure.md).** Under ADR-0008 the aggregate
*is* the mapping: `JdbcOrganoRepository` is a bare derived interface, so the moment
`termoId` joins the `OrganoDeContratacion` record, the generated SQL for `findAll`,
`findAllBySourceKeyIn` and `insert` names a column the schema does not have. (`update` and
`updateActive` take explicit parameters and are unaffected.) FEAT-0006's repository
integration tests then go red on a task that touches no infrastructure code — and because
neither `integrationTest` suite is part of `check`, `./gradlew build` would stay green
while CI failed.

Model and column are one cohesive change, and the placement's foreign key needs the
`termo` table to exist, so both tables land in one migration. Note this **reverses
FEAT-0006's own pairing**, where the migration shipped alongside the JDBC repository in its
TASK-0002; the reversal is deliberate, and a reader arriving from FEAT-0006 should not read
it as an inconsistency. The reverse split — migration first, model second — would also land
green twice, so this is a cohesion argument rather than the only workable order.

## Scope
- `Termo` record in `gal.conxugal.domain.organo`: system-assigned `UUID id`
  (`@GeneratedValue`, null until the database assigns it), `String name`, and a
  **nullable** `UUID parentId` — a null parent is a root, which is what makes the taxonomy
  many-rooted and arbitrarily deep. The parent is held as the parent's **id**, not a nested
  term, keeping the 1:1 single-table mapping of ADR-0008.
- `TermoRepository` port: **find all, in name order** — the read endpoint serves this list
  verbatim, so there is no by-parent or subtree query, and the order is part of the port's
  contract rather than the adapter's private choice: the feature promises it to HTTP callers,
  so a `findAll` free to return rows in any order would not satisfy the port's own consumers.
  Same for `OrganoRepository.findAll()`. Say so in the method's javadoc — plus find by id,
  insert, rename, re-parent,
  delete, an `existsByParentId`-style child check the delete rule needs, a
  **children-of-parent read** the sibling-name rule needs (roots included, so a null parent
  is a legal argument). No lock operation: the rules are use-case checks and the taxonomy is
  written to too rarely for contention to be a real condition (see the feature's
  *Edge cases*).
- Placement on the Órgano: a **nullable** `UUID termoId` on `OrganoDeContratacion`,
  and the `OrganoRepository` operations that write it — set the term, clear it, and clear
  every placement pointing at a given term (what `DeleteTermo` needs). Placement is exactly
  one term or none; there is no collection.
- A **by-id read** on `OrganoRepository` (`findById`/`existsById`). The port has none today
  — reconciliation matches on `sourceKey` — and
  [TASK-0004](TASK-0004-organo-classification-use-cases.md) must reject an unknown Órgano,
  which without it means a `findAll()` scan per assign: exactly the whole-table server work
  this feature is built to avoid.
- **No filtered reads.** There is deliberately no `findByTermo` and no `findUnclassified`:
  `GET /api/organos` returns `findAll()` with each row's `termoId`, and every
  by-term or unclassified view is a client-side filter over that list. Adding either query
  here would ship port surface with no caller.
- Keep the placement out of the reconciliation write paths: `update` and `updateActive`
  must go on writing name/active only, so an import cannot disturb a placement.
- **Migration** `V9__create_termo_and_organo_placement.sql` under
  `infrastructure/src/main/resources/db/migration/`, creating the `termo` table —
  UUID primary key defaulting to `uuidv7()` as `organo_contratacion` does,
  `name VARCHAR(255) NOT NULL` (matching the `@Size` the request records enforce, and the
  `organo_contratacion.name` precedent), and a nullable self-referencing `parent_id` with a
  foreign key back to `termo` — and adding the nullable `termo_id` to
  `organo_contratacion`
  with a foreign key to `termo`. Both tables in one migration: the placement's
  foreign key needs the term table to exist already. **V9, not V8** — V8 is taken, and V4
  is reserved by `db/migration-local/V4__seed_demo_user.sql`, so a free-looking number in
  `db/migration/` is not necessarily free.
- The same migration declares the **Galician ICU collation** both reads order under — a
  named collation (`CREATE COLLATION … provider = icu, locale = 'gl-ES'`) rather than a
  locale string repeated at each call site, so the ordering cannot drift between the two
  queries and the name is greppable. Declaring it here, with the tables, is what stops the
  order depending on how the cluster happened to be initialised: under the default C/POSIX
  collation, accented Galician names sort after `Z`.
- The collation is applied **as the column collation** on `termo.name` and on
  `organo_contratacion.name` (the latter by `ALTER COLUMN … TYPE … COLLATE`, since V6
  predates it), not left for each query to remember. Both ports order by name and both
  adapters derive that `ORDER BY` from the method name, so there is no hand-written query
  to attach an explicit `COLLATE` to — declared on the column, every derived read inherits
  it and the port's ordering contract holds without the adapter doing anything.
- The same migration adds a **`CHECK (parent_id IS DISTINCT FROM id)`** on `termo`. A
  multi-row cycle is `MoveTermo`'s own check to make; a term pointing at itself needs no
  second row and a foreign key is satisfied by a self-reference. It is the one cycle the
  schema can reject outright, so it does.
- The same migration adds a **unique index on `(parent_id, lower(name))` with
  `NULLS NOT DISTINCT`**, enforcing the feature's sibling-name rule in the one place a
  concurrent create cannot slip past. `NULLS NOT DISTINCT` is what extends it to the roots:
  under the default, every null `parent_id` is distinct and two identically-named roots
  would be accepted. `lower(name)` makes it case-insensitive, matching the rule as stated.
- The self-referencing `termo.parent_id` foreign key likewise carries **no
  `ON DELETE` action**. `CASCADE` here would let one delete remove a whole subtree, quietly
  defeating the R16 rule that a delete is *rejected* while the term has children — the
  refusal would never get the chance to fire.
- The foreign key on `organo_contratacion.termo_id` carries **no `ON DELETE`
  action** — not `SET NULL`, and never `CASCADE`. `DeleteTermo` clears the placements itself
  in the same transaction, and the bare foreign key is the backstop that turns a skipped
  clearing into a loud constraint violation rather than a silent mass-unclassify. This is
  settled in the feature's *Placement and classification* section; no later task revisits
  it.
- **Almost no JDBC repository code here.** `JdbcTermoRepository`, and every placement
  operation Micronaut Data can derive, are
  [TASK-0002](TASK-0002-taxonomia-store-infrastructure.md). The single exception is
  `clearPlacementsByTermo`: matching and clearing the same column has no derived-method
  form, so without its `@Query` here `infrastructure` would not compile against the widened
  port at all.
- Delete **`FakeOrganoRepository`** (`domain/src/test/.../organo/`) and stub
  `OrganoRepository` with Mockito in the tests that used it. The fake implements the port,
  so it stops compiling here, and its `update`/`updateActive` rebuild the record
  component-by-component: the quickest fix is to pass `null` for the new component, which
  would make the fake **silently drop placements on every reconciliation write** — the
  exact invariant SPEC-0004 #5 protects, defeated in the test double that is supposed to
  prove it. A mock cannot drift that way because it reimplements nothing, and the invariant
  is proved where it actually lives: against the generated SQL, in the repository
  integration suite.

## Acceptance criteria
- A `Termo` can be constructed as a root (null parent) or as a child of another
  term, and nothing in the model caps the nesting depth.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #14)
- A term holds at most one parent by construction — the model offers no way to give it a
  second. (SPEC-0004 #15)
- An `OrganoDeContratacion` carries either one term id or none; the type admits no
  second placement. (SPEC-0004 #17)
- An Órgano built without a placement is unclassified, and that is the state a newly
  imported one starts in. (SPEC-0004 #18)
- The port exposes every operation the later use cases need — find all, find by id, insert,
  rename, re-parent, delete, check for children, read a parent's children, set/clear an
  Órgano's term, clear the placements pointing at a term, and read an Órgano by id — and
  nothing they do not; no infrastructure type leaks into its signatures.
- The collation exists after the migration and orders accented Galician names as a reader
  expects — `Á` beside `A`, not after `Z` — asserted with a direct `ORDER BY … COLLATE`
  query, since a missing or misdeclared collation fails far from where it is used.
- Both name columns *carry* that collation, asserted the way a caller will actually meet
  it: a **bare `ORDER BY name`** — no explicit `COLLATE` — returns `Ávila` before `Zamora`,
  and `OrganoRepository.findAllOrderByName` returns `Ávila, Avión, Zamora`. Declaring the
  collation without applying it to the columns leaves every derived read on the cluster
  default, which is the whole failure this criterion exists to catch.
- The migration applies cleanly on top of the existing schema and creates both the
  `termo` table (with its self-referencing parent foreign key) and
  `organo_contratacion.termo_id` (nullable, foreign-keyed, no `ON DELETE` action).
- Deleting a term is **refused by the database**, not merely configured to be: deleting one
  that still has a child term, and deleting one an Órgano is still placed in, each raise a
  foreign-key violation naming the constraint — the loud failure the missing `ON DELETE`
  action is there to produce. A term nothing references still deletes.
- A term cannot be its own parent, on insert or on re-parent; both are refused by the
  check constraint rather than left to a use case that has not been written yet.
- The sibling-name index does what it claims, asserted directly against the database:
  two same-named children of one parent are refused, **two same-named roots are refused**
  (the `NULLS NOT DISTINCT` case, which a default index would silently allow), a
  case-only difference is refused, and the same name under two different parents is
  accepted. This is the feature's only race-proof guard on the rule, and it is one keyword
  away from not working.
- **The task lands green.** FEAT-0006's existing `JdbcOrganoRepository` integration tests
  still pass unchanged against the migrated schema — the widened record round-trips with
  `termoId` null — which is the whole reason the migration is in this task.
- `findAll` reports each Órgano's `termoId`: the term's id for a placed one, null for
  an unplaced one, including a freshly inserted Órgano. (SPEC-0004 #8, #18)
- The reconciliation write paths leave the placement alone: `update` and `updateActive`
  against a **placed** Órgano change name/active and leave `termo_id` intact — an
  import can neither move nor drop a placement, and an Órgano going inactive keeps its term.
  Verified in the repository integration suite, against the SQL Micronaut Data actually
  generates — the one place the guarantee is real, rather than in a test double that would
  be reimplementing the very invariant under test. (SPEC-0004 #5, #6)
- The domain model is unit-tested at the record level (construction, null-parent root,
  null-placement unclassified) without a database; the migration and the two criteria above
  are covered by the existing Testcontainers integration suite, which applies it on startup.
