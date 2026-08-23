---
feat: FEAT-0015
domain: backend
adrs: [0002, 0008, 0023]
status: todo
depends_on: [TASK-0004, TASK-0005]
---

# Licitacións store: the competition tables

`licitacion_participation` and `licitacion_ute_membership` — who bid, on which lote, whether they
won, and which firms made up a consortium. The half of R19 and R21 that is storage; the display
halves are the browsing feature's.

Operadores are the stored projection of
[ADR-0023](../../architecture/0023-operadores-as-a-stored-projection.md), so a participation holds a
**reference** to one and no copy of its name — with the single exception below, which is
**amendment 1** and is the only name this family displays.

## Scope

- **A migration** (next free `V` across `db/migration` **and** `db/migration-local`, taken at merge
  time) creating:
  - **`licitacion_participation`** — `id UUID PRIMARY KEY DEFAULT uuidv7()`,
    `licitacion_id NOT NULL`, a nullable `lote_id`, a nullable `operador_economico_id` FK, a
    **`won`** marker, a **`consortium`** marker, a nullable **`consortium_name`**, and the
    withdrawal marker;
  - **`licitacion_ute_membership`** — `participation_id` and `operador_economico_id`, plus the
    withdrawal marker, **primary key `(participation_id, operador_economico_id)`**.

  **The membership takes no surrogate `id`, and the participation does.** The participation's
  natural key carries two nullable components, so it can only be a unique constraint and the
  primary key has to be a surrogate — which is the `ParticipationId` a membership references. The
  membership's own key is two non-null foreign keys, so the pair *is* its identity and a surrogate
  beside it would be a second key naming the same row.
  [TASK-0004](TASK-0004-award-points-and-competition-value-types.md)'s `UteMembership` is therefore
  a value filed under its participation rather than an entity of its own, and compares by its
  components — **all three of them, the withdrawal marker included**. Two readings of one row
  either side of a withdrawal are therefore not interchangeable, which is a trap for any caller
  that collects memberships into a set. Narrowing that equality is not open to the record: every
  component the table keys on is another aggregate's identifier, which is exactly what the
  entity-identity architecture rule refuses an override for. A caller that needs the pair alone
  changes the rule rather than the record.
- **The membership's write is hand-written SQL, not a derived `save`.** `NomeAlternativo` is the
  shipped precedent for a composite key, and it proves only the *read* path — its writes are
  hand-written in `JdbcOperadorRepository` for exactly this reason. Micronaut Data models a
  composite identity first-class, but the `ON CONFLICT` this table's idempotence criterion needs is
  not something a derived method expresses.
- **The participation's `CHECK` is mirrored in the domain record**, which refuses a
  `consortiumName` alongside an operador reference or without the consortium marker. The constraint
  stays in the migration — it is the guarantee — but a parse defect then fails where the mistake
  is rather than at the insert, where under this feature's own rules it would send the whole
  procedure to the outstanding ledger. `JdbcOperadorRepository.retainName` is the precedent
  TASK-0005 already cites for that class of error. The record's refusal is one-directional too, so
  the consortium carrying no name is accepted by both.
- **Natural keys**, on the same reasoning as TASK-0005's: a participation upserts on
  `(licitacion_id, lote_id, operador_economico_id, consortium_name)` declared **`NULLS NOT
  DISTINCT`** — two of those four components are null for the 33-of-35 unidentified-consortium case,
  and PostgreSQL's default would insert a fresh row on every re-import.
- **`consortium_name` is R18's one exception, and the constraint is one-directional.** A `CHECK`
  forbids the name where the catalogue could have held the party:

  ```sql
  CHECK (consortium_name IS NULL OR (consortium AND operador_economico_id IS NULL))
  ```

  An earlier draft made this a biconditional — name **present** exactly when a consortium is
  unidentified — and that was wrong twice. It would **reject a real row**, since nothing measured
  guarantees every consortium's outer `<li>` carries a name, and a blank one would fail the insert
  and send the whole procedure to the ledger, against R33 and against this feature's rule everywhere
  else that an uninterpretable value stores null rather than rejecting. And it would **collide with
  [TASK-0013](TASK-0013-consortia-and-their-membership.md)**, where a consortium the formalisation
  identifies gains an operador after its participation was written from the bidder row. The
  one-directional form still rejects both cases the criteria below name.
- **Identifying a consortium clears its name in the same statement that sets the operador**, stated
  here because the constraint depends on it and TASK-0013 is what performs it.
- **A membership hangs off the participation, not off a UTE operador.** That is what makes one shape
  serve both branches: an identified UTE (2 of 35 measured on bidder rows) and an unidentified one
  store the same membership rows, and only the participation's operador reference differs. A
  membership keyed on two operadores could not express the 94% case at all.
- **A membership carries its own withdrawal marker.** SPEC-0006 R7 counts "one visible UTE
  membership" toward an operador's reachability, so a member firm whose only tie is a membership
  under a **withdrawn** participation would stay reachable through an invisible fact — which is what
  SPEC-0006 #39 tests for. Keeping the two in step is
  [TASK-0014](TASK-0014-reconciling-a-restated-procedure.md)'s; this task provides the column and
  the query that respects it.
- **Indexes: the participation's natural key, the membership's primary key, and the foreign keys.
  Nothing else.** An
  earlier draft added `(licitacion_id, lote_id)` "for the `Part.` cross-check and R21's page"; the
  cross-check never reads the database — TASK-0010 performs it at parse time, with no database at
  all — and R21's page is the browsing feature's read.
- **JDBC repositories** for both, behind ports in the domain.

**Out of scope:** consortium **detection** (TASK-0010, which reads the markup), consortium
**recording** (TASK-0013, which decides what goes in these rows), the propagation of a withdrawal
from a participation to its memberships (TASK-0014), and every read endpoint.

## Acceptance criteria

- A participation stores in all four shapes — single firm with an operador, single firm without one,
  consortium with an operador, consortium with a published name and no operador — and reads back
  distinguishable. ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #21 as amended)
- The `CHECK` **rejects** a participation carrying a `consortium_name` alongside an operador
  reference, and one carrying a name with `consortium` false. (SPEC-0008 #24 as amended)
- The `CHECK` **accepts** a consortium participation carrying no name — the row the source could
  publish and a biconditional would have refused. (SPEC-0008 #44)
- A `licitacion_ute_membership` stores against a participation whose operador reference is **null**,
  which is the 33-of-35 case and the one amendment 1 exists for. (SPEC-0008 #21 as amended)
- Two members of one consortium store two membership rows; **upserting the same member twice leaves
  one**, absorbed by the primary key rather than raising. (SPEC-0008 #17)
- A membership row carrying the withdrawal marker is excluded from a query for an operador's
  **visible** memberships. *The propagation that sets it is TASK-0014's; this is the storage half.*
  ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #39)
- Storing a procedure's participations twice leaves one row per published bidder per lote —
  including on a **lotless** procedure, where `lote_id` is null. (SPEC-0008 #17)
- Integration-tested against PostgreSQL (Testcontainers), including a migration test pinning both
  tables' exact column sets.
