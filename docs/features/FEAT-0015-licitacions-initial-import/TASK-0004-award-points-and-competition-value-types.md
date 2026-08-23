---
feat: FEAT-0015
domain: backend
adrs: [0008, 0019]
status: done
depends_on: [TASK-0003]
---

# Award points and competition value types

The R8 structure as types: **a lote where the procedure has them, the procedure itself where it does
not**, each carrying its classification, its award, its formalisation and its bidders. One place per
thing awarded, and **no second copy at procedure level**.

Types only — tables are
[TASK-0005](TASK-0005-licitacions-store-the-procedure-and-its-award-points.md)'s and
[TASK-0006](TASK-0006-licitacions-store-the-competition-tables.md)'s, and nothing parses into them
until [TASK-0009](TASK-0009-record-parse-awards-formalisation-and-classifications.md) and
[TASK-0010](TASK-0010-record-parse-bidders-and-consortium-detection.md).

**This task is the authoritative field list.** Under
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md) the
domain record *is* the mapping, so every column the two store tasks create must have a component
here — including the back-references and the withdrawal markers, which an earlier draft declared in
the tables and not in the types.

## Scope

- **Typed identifiers** under [ADR-0019](../../architecture/0019-typed-aggregate-identifiers.md) for
  every child that another row references: `LoteId` and `ParticipationId` at minimum, each a record
  wrapping a database-assigned `UUID` with its converter, on the `LicitacionId` shape
  [TASK-0003](TASK-0003-licitacion-domain-model.md) establishes.

  **The implementation took six rather than two, and the "at minimum" is why.** `Award`,
  `Formalisation`, `CpvClassification` and `NutClassification` are referenced by nothing, but each
  is a `@MappedEntity` and so needs an `@Id` of some kind — and the natural key each one upserts on
  (TASK-0005's table) carries a **nullable** `lote_id`, which PostgreSQL forbids in a primary key.
  TASK-0005 already declares those keys `UNIQUE … NULLS NOT DISTINCT`, which is a unique constraint
  and not a primary key, so each of the four takes a surrogate identity beside it: `AwardId`,
  `FormalisationId`, `CpvClassificationId`, `NutClassificationId`. Typed rather than a bare `UUID`
  because TASK-0003 typed all four vocabularies for the same reason, and a mixed idiom inside one
  package is the confusion ADR-0019 exists to prevent.

  **It also buys the equality a restatement needs.** An entity keyed on an identity compares by it,
  so an award whose amount is corrected or whose awardee moves to a different operador is still the
  same award — which is precisely what
  [TASK-0014](TASK-0014-reconciling-a-restated-procedure.md) must be able to conclude. Keyed on its
  contents instead, the corrected row would compare unequal to the row it corrects.

  **`UteMembership` is the one child that takes none**, and that is not an omission: TASK-0006
  gives it no `id` and a key of `(ute_id, operador_economico_id)`, both non-null, so the
  pair is what the table keys on. `EntityIdentityArchTest` then requires it **not** to override
  `equals`/`hashCode` — it is a value filed under its owner, on `NomeAlternativo`'s shape, and the
  record's own equality is the one it gets. *(The key was `(participation_id,
  operador_economico_id)` as first shipped; amendment 1 moved both ends onto the catalogue, and the
  reasoning here is unchanged because both are still another aggregate's identifier.)*

  **One consequence is named rather than left to be discovered**: that equality is the *triple*, so
  two readings of one row either side of a withdrawal compare unequal, and a caller collecting
  memberships into a set while a reconciliation flips markers holds one row twice. `NomeAlternativo`
  answers the same problem by comparing on its natural key alone, which is not open here — every
  component this table keys on is another aggregate's identifier, and that is precisely the case
  the arch rule refuses an override for. Nothing this feature builds notices; a caller that needs
  the pair alone changes the rule rather than the record.
- **`Lote`** — its `LicitacionId`, its **identifier as text**, optionally a description and an
  estimated value, and a withdrawal marker.

  **The identifier is text, not a number**, and this is measured rather than defensive: `OU0028`,
  `LU4001`, `LU4031` and `CO0642` were all observed in award-table lote cells over 240 procedures
  ([`design/source-contract.md`](design/source-contract.md)). An integer column would reject a real
  procedure. Both extras are optional because `Relación de lotes` was **empty** on procedure 822054
  while `Nº lotes` said `2` and the award table named both — a lote's *existence* comes from the
  award table; the lotes table supplies decoration.
- **`Cpv` and `Nut`** — the two regulated European lists a classification cites, each an entity
  with a table of its own on the `LicitacionState` shape: a system-assigned identity, the **code
  the list assigns** as its natural key, and a **nullable description that carries no unique
  constraint**.

  **They are vocabularies, not columns, because the lists are regulated and shared.** A CPV code is
  not this source's coinage and not a procedure's property — it is an external standard thousands
  of procedures cite — so it is held once and referred to. That is what lets
  [SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) R23 offer *only the codes a
  year's selection actually contains* as a reference rather than a `DISTINCT` over a column
  repeated once per procedure per code, and it is why a code must be an independent entity **to be
  referenced by others**.

  **An import matches an entry on its code and never on its description.** The code is what the
  list identifies an entry by; the description is wording — translated, revised, and repeated
  across sibling entries. A store unique on it would reject a real entry and matching on it would
  merge entries the list distinguishes, so it carries no constraint, exactly as the state's label
  carries none.

  **Nothing seeds either table.** Regulated is not the same as closed: CPV is versioned, and the
  2008 revision retired codes the 2003 one issued, while this system imports procedures published
  across both. A seeded catalogue would turn a code retired before our copy was taken into a
  foreign-key violation and a rejected procedure — the harm R33's store-as-published exists to
  prevent. An unseen code costs a row.

  **The description is nullable and nothing populates it yet.** The record's CPV and NUT tables
  publish the code alone ([`design/source-contract.md`](design/source-contract.md)), so an import
  stores the code and leaves the description null. It is declared now so the official wording has
  somewhere to go without a later migration; null means nobody has supplied one, never that the
  entry has none.
- **`CpvClassification` and `NutClassification`** — two records, not one, because they map two
  tables and one `@MappedEntity` record cannot map both. Each carries its `LicitacionId`, a
  **required reference to its `Cpv` or `Nut` entry**, its diffusion date, a **nullable `LoteId`**
  and a withdrawal marker. What the row holds is that *this* procedure cites *that* entry, on that
  date, for that award point — which is the only part of the fact that belongs to the procedure.

  The nullable lote is **amendment 2**, and it is the departure worth stating: on 822054 — two
  lotes, two separate awards — **every** CPV and NUT row's lote cell was procedure-wide. A model
  requiring a lote on every classification row of a procedure that has lotes could not store what
  the source publishes. #9's no-second-copy rule is untouched: the procedure-level row is the
  **only** row, not a duplicate of per-lote ones.
- **`Award`** — its `LicitacionId`, a nullable `LoteId`, the resolution, the resolution date, the
  awarded amount as `Money`, the stated execution period as published text, the **published awardee
  name**, a nullable operador reference, **how that operador was resolved**, and a withdrawal
  marker.

  The resolution path is a field, not a derivation, because
  [TASK-0012](TASK-0012-resolve-the-awardee.md) needs it distinguishable and reversible. Its four
  values are **totally ordered**, and the order is declared here because
  [TASK-0014](TASK-0014-reconciling-a-restated-procedure.md) gates a re-resolution write on it:

  ```text
  PUBLISHED_BY_FORMALISATION > PUBLISHED_BY_BIDDER > NAME_DERIVED > UNRESOLVED
  ```

  Built as `AwardeeResolutionPath`, an enum declared **weakest first** so the order is the enum's
  natural one and `supersedes` is `compareTo(…) > 0` — the `NomeRank.outranks` shape, so no caller
  has to read the comparison in the right direction. It is **never null**: `UNRESOLVED` is the
  value an award nothing resolved carries, so the absence of a link is stated rather than inferred
  from a null beside a null operador. It is named apart from the award's `resolution`, which is the
  published `Resolución` cell and a different thing entirely.

  The **awarded amount is the resolution table's `Importe` and nothing else.** The listing's
  `importe` is the base budget, and a model that let one stand in for the other would fill every
  R24 total with budgets, silently and plausibly.
- **`Formalisation`** — its `LicitacionId`, a nullable `LoteId`, the date, the contratista's
  published name, the fiscal identifier the cell carried (nullable), the nationality, the amount and
  a withdrawal marker. Its own type rather than folded into the award, because the two are separate
  publications that can disagree and [TASK-0012](TASK-0012-resolve-the-awardee.md) has a rule for
  when they do.
- **`Participation`** — its `LicitacionId`, a nullable `LoteId`, a nullable operador reference, a
  marker for **whether it won**, and a withdrawal marker.

  > **Superseded, and the code with it.** This task shipped a `Participation` carrying a
  > **consortium marker** and a **published consortium name**, plus a constructor refusal
  > mirroring a `CHECK` on the column — R18's one exception under the first reading of amendment 1.
  > Amendment 1 has since been restated: a UTE is an **operador económico**, so a consortium's
  > published name lives on its operador like every other party's and this family stores no
  > per-row name at all.
  > [TASK-0006](TASK-0006-licitacions-store-the-competition-tables.md) removes both components,
  > the refusal and the `CHECK`.
- **`UteMembership`** — one UTE operador, one member operador and a withdrawal marker.

  > **Superseded, and the code with it.** This task shipped a `UteMembership` keyed on a
  > `ParticipationId` and one member operador — hung off the bid, so that one shape served an
  > identified and an unidentified consortium alike. Under amendment 1 as restated **both ends are
  > operadores**, which is what lets the relation read in both directions rather than only from the
  > member's end. TASK-0006 re-keys it and moves it beside `OperadorRepository`, both of its ends
  > being catalogue entries. The equality reasoning below is unaffected: both components are still
  > another aggregate's identifier, so the record must still not override `equals`/`hashCode`.
- **The shared lote normaliser**, measured over 240 procedures and recorded in
  [`design/source-contract.md`](design/source-contract.md):

  | Table | Procedure-wide row | Per-lote rows |
  | --- | --- | --- |
  | Award | `_` × 189 | `1`…`7`, and `01`, `02`, `03`, `05` |
  | Formalisation | `_` × 99 | `1`…`10` |
  | NUT | `_` × 217 | `1`…`8` |
  | Bidders | `-` × 274 | `1`…`10` |

  So the rule is: **`_`, `-`, empty and blank all mean the procedure as a whole; leading zeros are
  stripped; what remains is compared as text.** Two things this corrects from an earlier draft —
  zero-padding varies *within* a table rather than between them (the award table produced both `1`
  and `05`), and the award table's procedure-wide cell is `_`, not empty as the source contract
  once recorded.

  Built as `LoteKey.normalise(String)` answering an `Optional<String>`, where **empty is the
  procedure-as-a-whole value** — the shape that maps straight onto the nullable `lote_id`. It
  normalises **for comparison and never for storage**: a lote published as `05` is stored as `05`,
  which is what TASK-0005's criterion asks for, and this is what lets a row spelling it `5` find
  that lote all the same. A cell of nothing but zeros keeps one, so `000` is the lote `0` rather
  than the procedure as a whole — a published lote must never reduce to the absence of one.

**Out of scope:** every table and repository, every parse, and the awardee resolution itself.

**The R8 invariant is not enforced by the model, and saying otherwise would be false.** Making
`lote` nullable on the award and the classification is precisely what makes a procedure-level row
and per-lote rows *coexpressible*; nothing here prevents both. What keeps the invariant is the
parse — one row out per source row in
([TASK-0009](TASK-0009-record-parse-awards-formalisation-and-classifications.md)) — and TASK-0005's
criterion is the regression test for it. A partial unique index would bound the procedure-level row
to one rather than exclude it, and genuine enforcement would need a trigger, which is heavier than
the problem.

## Acceptance criteria

- A classification constructs **with and without** a lote, and a procedure that has lotes accepts a
  classification carrying none. ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md)
  #9 as amended, #10)
- A classification **refers to** its `Cpv` or `Nut` entry and holds no code of its own, so two
  procedures citing one code refer to one entry; a classification citing no entry is refused, where
  a null would otherwise reach a `NOT NULL` foreign key whose error names the column rather than
  the mistake. (SPEC-0008 #23 storage half, #44)
- A `Cpv` constructs with a code the table has never held and with no description, two entries
  sharing one description stay two entries, and one entry read either side of its wording being
  supplied is the same entry. The same for `Nut`. Nothing seeds either, and nothing validates a
  code against a known set — the lists are versioned, not closed. (SPEC-0008 #44)
- A `Lote` constructs with the identifier `OU0028` and with `05`, and with no description and no
  estimated value. An integer identifier is not expressible. (SPEC-0008 #10, #44)
- Every type that a table stores carries its back-reference and its withdrawal marker, so no column
  in [TASK-0005](TASK-0005-licitacions-store-the-procedure-and-its-award-points.md) or
  [TASK-0006](TASK-0006-licitacions-store-the-competition-tables.md) is unmappable.
  ([ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md))
- An award and a formalisation each belong to exactly one award point — a lote, or the procedure
  where there are none — and no type holds a procedure-level copy of one that also exists per lote.
  (SPEC-0008 #9)
- The four resolution paths are totally ordered, and the order is a property of the type rather than
  a convention a caller applies: comparing any two answers which supersedes. (SPEC-0008 #46)
- An award with no operador constructs — R16 and R25 make an award that names nobody a supported
  outcome, not a failure. (SPEC-0008 #20)
- A participation constructs both with an operador and without one. *(Restated by TASK-0006: the
  four shapes this criterion named collapsed to two when the consortium became an operador.)*
  (SPEC-0008 #21 as amended)
- A `UteMembership` relates **one operador to another**, so an unidentified consortium's membership
  is expressible and reads from either end. *(Restated by TASK-0006, which re-keys it off the
  participation.)* (SPEC-0008 #21 as amended)
- The lote normaliser maps `_`, `-`, the empty string and a blank string to the same
  procedure-as-a-whole value; maps `01`, `1` and ` 1 ` to the same lote; and leaves `OU0028`
  intact. Unit-tested over **every spelling the four tables were measured to produce**.
  (SPEC-0008 #44)
- Unit-tested with no database and no HTTP.
