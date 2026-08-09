---
feat: FEAT-0010
domain: backend
adrs: [0002, 0008, 0018, 0019]
status: done
depends_on: [TASK-0002]
---

# Operador store: migration + JDBC repository

The `operador_economico` table and the driven adapter behind
[TASK-0002](TASK-0002-operador-domain-model.md)'s port. JDBC and SQL stay entirely in
`infrastructure` ([ADR-0002](../../architecture/0002-hexagonal-architecture.md),
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md)).

**This lands before `contrato_menor` is created**, and that ordering is the point. The foreign
key between the two tables is on the **contract** side, so FEAT-0009's store task creates
`contrato_menor` with a nullable `operador_economico_id` already on it, referencing this table. The
alternative — adding the column later — means an `ALTER` against a table this system expects to
reach millions of rows, which is the operation this project avoids by creating things while they
are empty. Nothing here references `contrato_menor`, so there is no cycle: this table simply
exists first.

> **The migration landed with [TASK-0002](TASK-0002-operador-domain-model.md)**, as
> `V12__create_operador_economico.sql`, so that its mapped entities were not left pointing at
> tables that did not exist. Both tables below are already created. What remains here is the
> **JDBC adapter** and the integration tests that prove it. Three things it settled that change
> the work below:
>
> - The retained-names table's `UNIQUE (operador_economico_id, name)` is its **primary key**,
>   because Micronaut Data needs an identity to read the association back. Without one the join
>   is generated correctly and then returns an **empty set** rather than failing — proved by
>   removing the key and watching the read come back empty. `ON CONFLICT (operador_economico_id,
>   name)` targets it either way.
> - `promoteName` replaces the plain name-and-rank update. It writes both **and** drops the name
>   from the retained set, because nothing else could: the aggregate refuses to be built holding
>   its own displayed name as an alternative, so a store that left that row behind would write a
>   state no later read could load.
> - Neither `promoteName` nor `retainName` derives from its name — `retain` is not a Micronaut
>   Data prefix, and `promoteName` spans two tables. Both need explicit queries, so this adapter
>   will **not** be an empty interface like `JdbcOrganoRepository`.
>
> **Three notes from the implementation.**
>
> - **No migration was written.** The `V` number the Scope reserves below was never taken: both
>   tables had already landed as `V12` with [TASK-0002](TASK-0002-operador-domain-model.md), for
>   the reason the callout above records. What shipped here is the adapter and the tests, and the
>   schema criteria are proved against `V12` rather than against a migration of this task's own.
> - **The retained-name upsert advances rather than overwrites.** `ON CONFLICT … DO UPDATE`
>   carries a `WHERE` that fires only when the incoming rank strictly outranks the stored one, so
>   a name is left carrying the **most recent** contract that published it rather than the last
>   one to arrive — which a walk reading newest first produces routinely. The condition coalesces
>   an absent date to `-infinity` so that it mirrors `NomeRank`: an undated rank loses to every
>   dated one and still orders against another undated one by source identifier. Comparing the
>   columns directly would answer `NULL` whenever either side is undated, and a `NULL` condition
>   skips the update silently. The strict comparison is also what makes re-reading a contract
>   already held cost nothing.
> - **The constraint criteria are proved in a migration test, not through the port.** A
>   deliberately violated constraint aborts the connection Micronaut Data shares with the adapter,
>   so those cases drive raw SQL and commit, as the termo and contrato menor migration tests
>   already do. The port offers no delete, so the foreign-key criterion had no other home either.

## Scope
- A migration (next free `V` number) creating `operador_economico`:
  - `id UUID PRIMARY KEY` — a plain `uuid` column; `OperadorId` is the Java type an
    `AttributeConverter` maps onto it
    ([ADR-0019](../../architecture/0019-typed-aggregate-identifiers.md));
  - `fiscal_id TEXT NOT NULL UNIQUE` — the identifier in R3's canonical form, trimmed and
    upper-cased. **The uniqueness is the requirement**, not a hint: it is what makes "the same
    operador never splits in two" hold at the store level rather than in whichever use case
    remembered to canonicalise first. **One column, matched on and displayed** — no second column
    holds a published spelling, which is the whole point of canonicalising;
  - `name TEXT NOT NULL` — the published name, stored as published;
  - the rank the name was taken from: `name_rank_date DATE` (nullable, and null ranks
    **last**) and `name_rank_source_id BIGINT NOT NULL`. The `name_` prefix is the point: R4
    ranks the name alone, so a bare `rank_` would read as ranking the row.
- A second table, `operador_economico_nome_alternativo`, holding the names R15 retains beside the
  principal one:
  - `operador_economico_id UUID NOT NULL REFERENCES operador_economico(id)`;
  - `name TEXT NOT NULL` — the published name, stored as published;
  - `last_published_date DATE` (nullable, null ranks **last**) and
    `last_published_source_id BIGINT NOT NULL` — the same rank pair the operador row carries, so
    the principal name and the alternatives order under one rule;
  - **`UNIQUE (operador_economico_id, name)` — this is the requirement, not a hint.** Without it
    the table grows one row per contract instead of one per distinct name, and the largest
    operador would hold tens of thousands of rows saying the same thing. It is also what makes
    the retention idempotent under re-import (#37) at the store rather than in the caller.
  - No index beyond that constraint's: nothing in this feature reads the table, and R8's lookup
    is a later feature's to measure and index for.
- **No column classifies an operador** — nothing records whether the awardee is a natural person
  or a legal entity, deliberately and although the published identifier makes it inferable (R6).
- The Micronaut Data JDBC implementation of `OperadorRepository`: find by fiscal id, insert, the
  combined name-and-rank update, and **retaining a name** as one
  `INSERT … ON CONFLICT (operador_economico_id, name) DO UPDATE` that advances the date and source
  id. One statement, so a name already held is advanced rather than rejected, and no caller has to
  read first and race.
- **No `contrato_menor` reference of any kind** — neither a column here nor an `ALTER` there.
  FEAT-0009's store task owns that column;
  [TASK-0004](TASK-0004-derivation-during-import.md) is what populates it.
- **The `UNIQUE (operador_economico_id, name)` constraint is what scopes a name to its operador,
  and it is the only thing that does.** `NomeAlternativo` is a value inside the aggregate and
  compares on its name alone, so nothing in the domain stops a name filed under one operador from
  being compared with a name filed under another — reads must not assemble alternatives from more
  than one operador into a single set.

## Acceptance criteria
- Inserting a second operador with an existing fiscal identifier fails at the unique constraint
  without altering the existing row.
  ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #3, one-operador half)
- `findByFiscalId` returns the stored operador for the canonical form of any of its published
  spellings, and nothing for one differing by internal spacing, punctuation or a character.
  (SPEC-0006 #3, #4)
- A stored operador reads back with its identifier **upper-cased**, whatever case it was written
  from — the store holds no other spelling to return. (SPEC-0006 #7, #30 exception)
- An operador inserted with a null id comes back carrying the generated `OperadorId`, and reads
  back equal — the converter round-trips through `@GeneratedValue`, the mechanism
  [FEAT-0009 TASK-0003](../FEAT-0009-contratos-menores-initial-import/TASK-0003-contrato-menor-domain-model.md)
  establishes and ADR-0019 rests on.
- The name-and-rank update writes all three values or none; a row never carries a name from
  one contract and a rank from another. (SPEC-0006 #7)
- An operador whose winning contract has no publication date stores a null rank date and is still
  found, updated and compared correctly. (SPEC-0006 #7)
- Retaining a name an operador already holds **advances its date and source id in place** and adds
  no second row; retaining a name it does not hold adds one. Asserted by row count, not only by
  the values. (SPEC-0006 #34)
- Retaining the **same name twice with the same contract** leaves the row byte-identical — the
  upsert is idempotent, which is what makes a re-import cost nothing here. (SPEC-0006 #37)
- Two names differing only in letter case are stored as **two rows** under one operador: the
  unique constraint is on the published name, so nothing folds case at the store any more than in
  the domain. (SPEC-0006 #35)
- Deleting an operador is not offered, and the foreign key means an alternative name cannot
  outlive the operador it belongs to.
- Integration-tested against PostgreSQL (Testcontainers), including the unique-constraint case
  and the name upsert's advance-not-duplicate behaviour.
