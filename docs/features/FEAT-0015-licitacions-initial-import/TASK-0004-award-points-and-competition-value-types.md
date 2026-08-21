---
feat: FEAT-0015
domain: backend
adrs: [0008, 0019]
status: todo
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
- **`Lote`** — its `LicitacionId`, its **identifier as text**, optionally a description and an
  estimated value, and a withdrawal marker.

  **The identifier is text, not a number**, and this is measured rather than defensive: `OU0028`,
  `LU4001`, `LU4031` and `CO0642` were all observed in award-table lote cells over 240 procedures
  ([`design/source-contract.md`](design/source-contract.md)). An integer column would reject a real
  procedure. Both extras are optional because `Relación de lotes` was **empty** on procedure 822054
  while `Nº lotes` said `2` and the award table named both — a lote's *existence* comes from the
  award table; the lotes table supplies decoration.
- **`CpvClassification` and `NutClassification`** — two records, not one, because they map two
  tables and one `@MappedEntity` record cannot map both. Each carries its `LicitacionId`, its code,
  its diffusion date, a **nullable `LoteId`** and a withdrawal marker.

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

  The **awarded amount is the resolution table's `Importe` and nothing else.** The listing's
  `importe` is the base budget, and a model that let one stand in for the other would fill every
  R24 total with budgets, silently and plausibly.
- **`Formalisation`** — its `LicitacionId`, a nullable `LoteId`, the date, the contratista's
  published name, the fiscal identifier the cell carried (nullable), the nationality, the amount and
  a withdrawal marker. Its own type rather than folded into the award, because the two are separate
  publications that can disagree and [TASK-0012](TASK-0012-resolve-the-awardee.md) has a rule for
  when they do.
- **`Participation`** — its `LicitacionId`, a nullable `LoteId`, a nullable operador reference, a
  marker for **whether it won**, a **consortium marker**, a **published consortium name** and a
  withdrawal marker.

  The consortium name is **R18's one exception**, and it is exactly one field on exactly one row
  type. R18 holds that this family stores no name of its own because a name belongs on the operador
  an identifier resolves to; an unidentified consortium has no such operador, so the alternative to
  storing its published name is losing it. This is **amendment 1**.
- **`UteMembership`** — a `ParticipationId`, one member operador and a withdrawal marker. **Hung off
  the participation**, never off a UTE operador, so one shape serves an identified and an
  unidentified consortium alike, and a membership's visibility can follow its participation's.
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
- A participation constructs in all four shapes: single firm with an operador, single firm without
  one, consortium with an operador, and consortium with a published name and no operador.
  (SPEC-0008 #21 as amended)
- A `UteMembership` references its **participation**, not an operador-to-operador pair, so an
  unidentified consortium's membership is expressible. (SPEC-0008 #21 as amended)
- The lote normaliser maps `_`, `-`, the empty string and a blank string to the same
  procedure-as-a-whole value; maps `01`, `1` and ` 1 ` to the same lote; and leaves `OU0028`
  intact. Unit-tested over **every spelling the four tables were measured to produce**.
  (SPEC-0008 #44)
- Unit-tested with no database and no HTTP.
