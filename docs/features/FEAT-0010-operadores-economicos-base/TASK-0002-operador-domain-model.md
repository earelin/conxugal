---
feat: FEAT-0010
domain: backend
adrs: [0002, 0008, 0018, 0019]
status: todo
depends_on: []
---

# `OperadorEconomico` domain model + repository port

The aggregate the catalogue is made of, and the port that stores it. Domain only — the schema
and the JDBC adapter are [TASK-0003](TASK-0003-operador-store.md)'s. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md), by
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md)
(1:1 to its own table, carrying its own mapping annotations), by
[ADR-0018](../../architecture/0018-operadores-as-a-stored-projection.md) (the catalogue is
stored state the import maintains, not a view computed on read) and by
[ADR-0019](../../architecture/0019-typed-aggregate-identifiers.md) (a new aggregate takes a
typed identifier).

**This task has no dependencies, and that is deliberate: it is what unblocks
[FEAT-0009](../FEAT-0009-contratos-menores-initial-import/README.md).** Its
`TASK-0003` declares the `@Relation(MANY_TO_ONE)` to `OperadorEconomico`, so the type must exist
before the contract aggregate can be written. The aggregate here **stores** the match key and the
rank as values; the functions that compute and compare them are
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
  | `matchKey` | `String` | The reduction [TASK-0001](TASK-0001-matching-emptiness-and-ranking-rules.md) computes. **Never displayed** (R13); the store is unique on it |
  | `displayName` | `String` | The awardee name **as published** by the winning contract |
  | `displayFiscalId` | `String` | The fiscal identifier **as published** by that same contract — padding and casing intact |
  | `rank` | the rank pair | Which contract those two came from: its publication date (nullable) and its source identifier |

- **Storing the rank is what makes R4 deterministic across runs** (#7). Without it, *is this
  contract newer than whatever won last time?* has no answer once the winning contract is out of
  hand, and two imports over the same data could disagree about the spelling.
- The display fields and the match key are **different things that look alike**, and the
  aggregate is where that is enforced: nothing derives one from the other, and the match key
  never leaves the domain.
- `OperadorRepository` port: `findByMatchKey(String)`, `insert(...)`, and an update of the
  display fields **and** the rank together — they move as one, or the row remembers a spelling
  from one contract and a rank from another.
- No classification of any kind. No column, field or method records whether an awardee is a
  natural person or a legal entity, and nothing branches on the shape of an identifier beyond
  TASK-0001's emptiness test (R6).

## Acceptance criteria
- The aggregate carries a match key distinct from both published spellings, and the identity is
  an `OperadorId` distinct from either.
  ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #2)
- Published values round-trip unchanged: an operador built from a padded, lower-case identifier
  keeps that exact spelling in `displayFiscalId` while matching on the reduced key.
  (SPEC-0006 #3, #7)
- Advancing an operador's display moves the name, the identifier spelling **and** the rank
  together, or moves none of them — the aggregate offers no way to write one without the others.
  *Deciding* whether a contract outranks the incumbent is TASK-0001's comparison, applied by
  TASK-0004; this task only makes the three inseparable. (SPEC-0006 #7)
- No stored attribute records whether the awardee is a natural person or a legal entity.
  (SPEC-0006 #10, stored-attribute half)
- Unit-tested without a database or HTTP server.
