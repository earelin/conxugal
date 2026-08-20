---
feat: FEAT-0010
domain: backend
adrs: [0002, 0008, 0019, 0023]
status: done
depends_on: []
---

# `OperadorEconomico` domain model + repository port

The aggregate the catalogue is made of, and the port that stores it. Domain only — the schema
and the JDBC adapter are [TASK-0003](TASK-0003-operador-store.md)'s. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md), by
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md)
(1:1 to its own table, carrying its own mapping annotations), by
[ADR-0023](../../architecture/0023-operadores-as-a-stored-projection.md) (the catalogue is
stored state the import maintains, not a view computed on read) and by
[ADR-0019](../../architecture/0019-typed-aggregate-identifiers.md) (a new aggregate takes a
typed identifier).

**This task has no dependencies, and that is deliberate: it is what unblocks
[FEAT-0009](../FEAT-0009-contratos-menores-initial-import/README.md).** Its
`TASK-0003` declares the `@Relation(MANY_TO_ONE)` to `OperadorEconomico`, so the type must exist
before the contract aggregate can be written. The aggregate here **stores** the canonical fiscal
identifier and the rank as values; the functions that compute and compare them are
[TASK-0001](TASK-0001-matching-emptiness-and-ranking-rules.md)'s, and nothing needs them until
[TASK-0004](TASK-0004-derivation-during-import.md) resolves an award — so TASK-0001 is not on the
path into FEAT-0009 and the two can be built in either order.

## Scope
- **`OperadorId`**, a record wrapping a `UUID`, with its `AttributeConverter` — ADR-0019's
  pattern for a new aggregate. It is also the type
  [FEAT-0009](../FEAT-0009-contratos-menores-initial-import/README.md)'s `ContratoMenor` carries
  as its optional operador reference, which is why this task lands before that aggregate is
  written.
- The `OperadorEconomico` aggregate:

  | Field | Type | Notes |
  | --- | --- | --- |
  | `id` | `OperadorId` | System-assigned, `null` only until the database assigns it |
  | `fiscalId` | `String` | The fiscal identifier in R3's **canonical form** — trimmed and upper-cased by [TASK-0001](TASK-0001-matching-emptiness-and-ranking-rules.md). **Matched on and displayed**; the store is unique on it |
  | `name` | `String` | The awardee name **as published** by the winning contract, untouched |
  | `rank` | the rank pair | Which contract those two came from: its publication date (nullable) and its source identifier |
  | `nomesAlternativos` | `Set<NomeAlternativo>` | Every **other** distinct name its contracts have published (R15) — empty when every contract published the principal name |

- **`NomeAlternativo`** — a published name plus **the same rank pair** the aggregate carries: the
  publication date (nullable) and source identifier of the most recent contract that published
  that name. Declared beside the aggregate, mapped `@Relation(ONE_TO_MANY)`.
  - **It carries the rank pair, not just a date.** R4 breaks date ties on the higher contract
    identifier and ranks undated contracts last, so a name holding only a date could not be
    ordered against a name sharing it, and two names seen only on undated contracts could not be
    ordered at all. Carrying both means the principal name and the alternatives compare under
    **`TASK-0001`'s one rank comparison** — the same function, not a second ordering that could
    drift from it.
  - **Distinctness is by the published name exactly**, no folding of case or spacing: R13 forbids
    normalising a name, and two spellings that differ are two names. The type must not reuse
    the identifier's canonicalisation — that reduction exists for identifiers and never for names.
  - The principal name is **not** repeated here. `name` holds the R4 winner and this set
    holds the rest, so the invariant is *no alternative equals the principal*, and promoting one
    means moving a value between the two rather than choosing among a set that contains both.
  - **One value per distinct name is enforced by `NomeAlternativo`'s identity, not by the
    aggregate.** It is a value inside this aggregate rather than an entity of its own: **the name
    is the identity**, and neither `lastPublished` nor the `operadorEconomicoId` column enters
    into it — that column files the row under its operador and completes the table's key, not the
    value's. A `Set` therefore cannot hold the same name twice and the aggregate needs no
    duplicate check. The consequence a caller must respect: building a set from the same name at
    two ranks silently keeps one of them, and **which rank survives is undefined** — resolve the
    rank before the set is built, never after.

- **Storing the rank is what makes R4 deterministic across runs** (#7). Without it, *is this
  contract newer than whatever won last time?* has no answer once the winning contract is out of
  hand, and two imports over the same data could disagree about the name.
- **The name is published, the identifier is canonical, and the aggregate keeps that asymmetry
  visible.** Nothing folds a name and nothing preserves an identifier's published case; a single
  helper doing "normalise a string" for both would erase the distinction the two requirements
  rest on.
- `OperadorRepository` port: `findByFiscalId(String)`, `insert(...)`, an update of the
  name **and** the rank together — they move as one, or the row remembers a name
  from one contract and a rank from another — and **retaining a published name**, which either
  adds an alternative or advances the rank of one already held (R15). One operation, not
  find-then-write: the store decides which of the two happened, so no caller can read, lose the
  race and insert a duplicate the unique constraint would then reject.
- No classification of any kind. No column, field or method records whether an awardee is a
  natural person or a legal entity, and nothing branches on the shape of an identifier beyond
  TASK-0001's emptiness test (R6).

## Acceptance criteria

> **One criterion below moved to [TASK-0001](TASK-0001-matching-emptiness-and-ranking-rules.md).**
> Ordering a `NomeAlternativo` against the aggregate's own rank needs the rank comparison, and
> this task deliberately ships none — the pair is carried as a value, and nothing orders it yet.
> The criterion is unproved here and is TASK-0001's to prove; everything else in the list is
> covered by `OperadorEconomicoTest` and `NomeAlternativoTest`.
- The aggregate carries **one** fiscal identifier, and the identity is an `OperadorId` distinct
  from it. There is no second representation of the identifier to pick between.
  ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #2)
- An operador built from a padded, lower-case identifier holds the **canonical** form and is
  matched by it: the published case is retained nowhere, and no accessor returns the spelling it
  was built from. (SPEC-0006 #3, #7)
- The **name** round-trips unchanged — internal spacing, casing and punctuation as published —
  so nothing about the aggregate canonicalises anything but the identifier. (SPEC-0006 #30)
- Advancing an operador's display moves the name **and** the rank together, or moves neither —
  the aggregate offers no way to write one without the other. *Deciding* whether a contract
  outranks the incumbent is TASK-0001's comparison, applied by TASK-0004; this task only makes
  the two inseparable. (SPEC-0006 #7)
- Two names differing only in **letter case or internal spacing** are two distinct
  `NomeAlternativo` values, never merged — asserted as its own case, since folding them would
  invent the canonical form R13 forbids. (SPEC-0006 #35)
- A `NomeAlternativo` compares against the aggregate's own rank through **TASK-0001's comparison**
  — the principal name sorts above an alternative sharing its date but carrying a lower contract
  identifier, and above one from an undated contract. The two orderings are the same function, so
  the retained names cannot disagree with R4. (SPEC-0006 #36)
- The aggregate never holds an alternative **equal to its principal name**; advancing the display
  to a new winner leaves the previous principal retained as an alternative and the new one absent
  from the set. (SPEC-0006 #33)
- No stored attribute records whether the awardee is a natural person or a legal entity.
  (SPEC-0006 #10, stored-attribute half)
- Unit-tested without a database or HTTP server.
