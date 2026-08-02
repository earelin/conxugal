---
feat: FEAT-0010
domain: backend
adrs: [0002, 0008, 0018, 0019]
status: todo
depends_on: [TASK-0002]
---

# Operador store: migration + JDBC repository

The `operador` table and the driven adapter behind
[TASK-0002](TASK-0002-operador-domain-model.md)'s port. JDBC and SQL stay entirely in
`infrastructure` ([ADR-0002](../../architecture/0002-hexagonal-architecture.md),
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md)).

**This lands before `contrato_menor` is created**, and that ordering is the point. The foreign
key between the two tables is on the **contract** side, so FEAT-0009's store task creates
`contrato_menor` with a nullable `operador_id` already on it, referencing this table. The
alternative — adding the column later — means an `ALTER` against a table this system expects to
reach millions of rows, which is the operation this project avoids by creating things while they
are empty. Nothing here references `contrato_menor`, so there is no cycle: this table simply
exists first.

## Scope
- A migration (next free `V` number) creating `operador`:
  - `id UUID PRIMARY KEY` — a plain `uuid` column; `OperadorId` is the Java type an
    `AttributeConverter` maps onto it
    ([ADR-0019](../../architecture/0019-typed-aggregate-identifiers.md));
  - `match_key TEXT NOT NULL UNIQUE` — **the uniqueness is the requirement**, not a hint. It is
    what makes "the same operador never splits in two" hold at the store level rather than in
    whichever use case remembered to reduce the identifier first;
  - `display_name TEXT NOT NULL`, `display_fiscal_id TEXT NOT NULL` — published spellings;
  - the rank the display fields were taken from: `rank_publication_date DATE` (nullable, and
    null ranks **last**) and `rank_publication_id BIGINT NOT NULL`.
- **No column classifies an operador** — nothing records whether the awardee is a natural person
  or a legal entity, deliberately and although the published identifier makes it inferable (R6).
- The Micronaut Data JDBC implementation of `OperadorRepository`: find by match key, insert, and
  the combined display-and-rank update.
- **No `contrato_menor` reference of any kind** — neither a column here nor an `ALTER` there.
  FEAT-0009's store task owns that column;
  [TASK-0004](TASK-0004-derivation-during-import.md) is what populates it.

## Acceptance criteria
- Inserting a second operador with an existing match key fails at the unique constraint without
  altering the existing row.
  ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #3, one-operador half)
- `findByMatchKey` returns the stored operador for a key reduced from any of its published
  spellings, and nothing for a key that differs by internal spacing, punctuation or a character.
  (SPEC-0006 #3, #4)
- An operador inserted with a null id comes back carrying the generated `OperadorId`, and reads
  back equal — the converter round-trips through `@GeneratedValue`, the mechanism
  [FEAT-0009 TASK-0003](../FEAT-0009-contratos-menores-initial-import/TASK-0003-contrato-menor-domain-model.md)
  establishes and ADR-0019 rests on.
- The display-and-rank update writes all four values or none; a row never carries a spelling from
  one contract and a rank from another. (SPEC-0006 #7)
- An operador whose winning contract has no interpreted date stores a null rank date and is still
  found, updated and compared correctly. (SPEC-0006 #7)
- Integration-tested against PostgreSQL (Testcontainers), including the unique-constraint case.
