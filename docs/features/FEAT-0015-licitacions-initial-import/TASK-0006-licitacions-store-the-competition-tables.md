---
feat: FEAT-0015
domain: backend
adrs: [0002, 0008, 0023]
status: done
depends_on: [TASK-0004, TASK-0005]
---

# Licitacións store: the competition tables

`licitacion_participation` and `operador_ute_membership` — who bid, on which lote, whether they
won, and which firms made up a consortium. The half of R19 and R21 that is storage; the display
halves are the browsing feature's.

**A UTE is an operador económico, not a property of a bid.** That is
[amendment 1](README.md#the-amendments-this-feature-rests-on) as it now stands, and it is what
this task builds against. Every party a bidder row names — a single firm, a member firm, a
consortium — resolves to an operador under
[SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) R3, so a participation holds a
**reference** and no copy of any name. There is no exception left: the published name of a
consortium the source declines to identify lives on **its** operador, like every other name.

## Scope

- **The catalogue change amendment 1 needs**, on `operador_economico`:
  - **`fiscal_id` becomes nullable.** R3 admits one party identified without an identifier — a UTE
    the source declines to identify, keyed on the bid it made. `UNIQUE` is unaffected: PostgreSQL
    treats nulls as distinct by default, which is exactly the semantics wanted here, so every real
    identifier stays unique while identifier-less UTEs coexist without colliding. **Do not** write
    `NULLS NOT DISTINCT` on this constraint — it would collapse every unidentified UTE onto one
    row, which is the pooling SPEC-0006 #9 forbids.
  - **a `ute` marker**, not null, defaulting to false. SPEC-0006 R6 refuses to store *which kind*
    an operador is; R6 as amended admits this one, because the source **publishes** it structurally
    (the nested `<ul>`) rather than the system deriving it from an identifier's shape.
  - **a `CHECK` binding the two**: `fiscal_id IS NOT NULL OR ute`. Without it the column reads as
    optional for everybody, and a resolution defect would quietly catalogue an ordinary firm the
    catalogue can never find again. The domain record refuses the same shape so a defect fails
    where the mistake is; this is the guarantee behind it, exactly as the participation's `CHECK`
    was under the previous model.
- **A migration** (next free `V` across `db/migration` **and** `db/migration-local`, taken at merge
  time) making that change and creating:
  - **`licitacion_participation`** — `id UUID PRIMARY KEY DEFAULT uuidv7()`,
    `licitacion_id NOT NULL`, a nullable `lote_id`, a nullable `operador_economico_id` FK, a
    **`won`** marker and the withdrawal marker. **No consortium marker and no consortium name** —
    both moved to the operador, and with them the `CHECK` an earlier draft carried;
  - **`operador_ute_membership`** — `ute_id` and `operador_economico_id`, both non-null FKs to
    `operador_economico`, plus the withdrawal marker, **primary key
    `(ute_id, operador_economico_id)`**, and a `CHECK` that the two ends differ. A consortium that
    was its own member would be a row saying nothing, and it would let one keep *itself* reachable
    under a predicate that counts a single visible membership. The record refuses the same shape.

  **The membership takes no surrogate `id`, and the participation does.** The participation's
  natural key carries two nullable components, so it can only be a unique constraint and the
  primary key has to be a surrogate. The membership's own key is two non-null foreign keys, so the
  pair *is* its identity and a surrogate beside it would be a second key naming the same row.
  [TASK-0004](TASK-0004-award-points-and-competition-value-types.md)'s `UteMembership` is therefore
  a value rather than an entity of its own, and compares by its components — **all three of them,
  the withdrawal marker included**. Two readings of one row either side of a withdrawal are
  therefore not interchangeable, which is a trap for any caller that collects memberships into a
  set. Narrowing that equality is not open to the record: every component the table keys on is
  another aggregate's identifier, which is exactly what the entity-identity architecture rule
  refuses an override for.
- **The membership's write is hand-written SQL, not a derived `save`.** `NomeAlternativo` is the
  shipped precedent for a composite key, and it proves only the *read* path — its writes are
  hand-written in `JdbcOperadorRepository` for exactly this reason. Micronaut Data models a
  composite identity first-class, but the `ON CONFLICT` this table's idempotence criterion needs is
  not something a derived method expresses.
- **Natural key**, on TASK-0005's reasoning: a participation upserts on
  `(licitacion_id, lote_id, operador_economico_id)` declared **`NULLS NOT DISTINCT`** — two of
  those three components are null on the ordinary lotless procedure whose bidder resolved to
  nobody, and PostgreSQL's default would insert a fresh row on every re-import.
- **Both ends of a membership are operadores**, and that is the whole of the change. It is what
  lets the relation read in **both** directions — *who was this consortium made of* and *what has
  this firm been part of* — for an identified UTE and an unidentified one alike. The earlier model
  hung the membership off the participation, which answered the second question and left the first
  with no page to answer it from.
- **A membership carries its own withdrawal marker.** SPEC-0006 R7 counts "one visible UTE
  membership" toward an operador's reachability, so a member firm whose only tie is a membership no
  visible bid still publishes would stay reachable through an invisible fact — which is what
  SPEC-0006 #39 tests for. **Setting it is
  [TASK-0014](TASK-0014-reconciling-a-restated-procedure.md)'s**, and the rule there is *"no
  visible bid of this UTE publishes it"* rather than *"its bid was withdrawn"* — trivial for the
  33-of-35 case, where the UTE exists per bid, and not for an identified UTE several procedures
  publish. This task provides the column and the query that respects it.
- **Indexes: the participation's natural key, the membership's primary key, and the foreign keys.
  Nothing else.** An earlier draft added `(licitacion_id, lote_id)` "for the `Part.` cross-check
  and R21's page"; the cross-check never reads the database — TASK-0010 performs it at parse time,
  with no database at all — and R21's page is the browsing feature's read.
- **JDBC repositories** for both, behind ports in the domain. The membership's port belongs beside
  `OperadorRepository` rather than in the licitación package: both of its ends are catalogue
  entries, and no licitación appears in it.

**Out of scope:** consortium **detection** (TASK-0010, which reads the markup), consortium
**cataloguing** (TASK-0013, which decides which operador a bid resolves to and mints the
identifier-less UTE), the withdrawal propagation (TASK-0014), and every read endpoint. In
particular **nothing here creates an operador**: this task makes the column nullable and the
membership storable, and TASK-0013 is what writes an identifier-less row.

## Acceptance criteria

- An operador stores with **no fiscal identifier** and the `ute` marker set, and a second one
  stores beside it — neither colliding with the other nor with any identified operador, asserted
  with all three in the table at once. (SPEC-0006 #40 as amended)
- The `ute` marker **survives a round trip** through the adapter, read back rather than only
  written: the record carries a constructor of the arity the aggregate had before it gained the
  marker, so a mapping that bound the wrong one would answer false for every operador and stay
  invisible. (SPEC-0006 #40 as amended)
- Two operadores **cannot** share one fiscal identifier, unchanged: the `UNIQUE` still refuses a
  duplicate, and making the column nullable did not weaken it. (SPEC-0006 #7)
- An operador that is **not** a consortium and carries no fiscal identifier is **refused** by the
  `CHECK` — the column is optional for one party and not for the rest. (SPEC-0006 #8)
- A participation stores in both shapes — with an operador and without one — and reads back
  distinguishable. ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #21 as amended)
- A participation carries **no name and no consortium marker**: a migration test pins its exact
  column set. (SPEC-0008 #24 as amended)
- An `operador_ute_membership` stores against a UTE operador whose `fiscal_id` is **null**, which
  is the 33-of-35 case and the one amendment 1 exists for. (SPEC-0008 #21 as amended)
- Two members of one consortium store two membership rows; **upserting the same member twice leaves
  one**, absorbed by the primary key rather than raising. (SPEC-0008 #17)
- The membership reads from **both ends**: the members of one UTE, and the UTEs one firm belongs
  to. (SPEC-0006 #40 as amended)
- A membership row carrying the withdrawal marker is excluded from a query for an operador's
  **visible** memberships. *The propagation that sets it is TASK-0014's; this is the storage half.*
  (SPEC-0006 #39)
- Storing a procedure's participations twice leaves one row per published bidder per lote —
  including on a **lotless** procedure, where `lote_id` is null. (SPEC-0008 #17)
- Integration-tested against PostgreSQL (Testcontainers), including a migration test pinning both
  tables' exact column sets and the altered `operador_economico`.
