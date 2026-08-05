---
feat: FEAT-0009
domain: backend
adrs: [0002, 0008, 0019]
status: done
depends_on: [TASK-0003]
---

# Contratos menores store: migration + JDBC repository

The schema and driven adapter behind [TASK-0003](TASK-0003-contrato-menor-domain-model.md)'s
port. **The prerequisite outside this feature was already met:** this called for
[FEAT-0010 TASK-0003](../FEAT-0010-operadores-economicos-base/TASK-0003-operador-store.md)
to create the `operador_economico` table this one's foreign key points at, but that migration
landed early with
[FEAT-0010 TASK-0002](../FEAT-0010-operadores-economicos-base/TASK-0002-operador-domain-model.md)
(`733b98e`), so that the operador entities were not left pointing at tables that did not exist.
Only the operador *adapter* is still outstanding there, and nothing here needs it. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md) and
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md);
JDBC and SQL stay entirely in `infrastructure`.

## Scope
- A migration (next free `V` number) creating `contrato_menor`:
  - `id UUID PRIMARY KEY` — the column is a plain `uuid`; `ContratoMenorId` is a Java type that
    an `AttributeConverter` maps onto it
    ([ADR-0019](../../architecture/0019-typed-aggregate-identifiers.md)), so nothing in the
    schema knows about the wrapper;
  - `source_id BIGINT NOT NULL UNIQUE` — R12's "no duplicates" enforced **at the store**,
    so it holds even if use-case logic slips, exactly as `source_key` does for the catalogue;
  - `organo_id UUID NOT NULL REFERENCES organo_contratacion(id)`;
  - `publication_date DATE` — nullable, and the **only** date column: the source's `DD-MM-YYYY`
    text is interpreted at the adapter and not stored (TASK-0003 records what that costs against
    R27);
  - `obxecto TEXT`, `amount NUMERIC`, `duration TEXT` — **nullable**, and none of them bounded:
    the source publishes no maximum for the object, and the duration's 64-character cap is
    applied in Java at the adapter ([TASK-0005](TASK-0005-source-port-and-adapter.md)), where an
    over-long value loses its tail rather than failing a batch and rejecting a real award (#42).
    This task originally mirrored that cap as a `VARCHAR(64)` backstop. The column is `TEXT`
    instead, because a bound here can only ever produce the outcome the cap exists to avoid — an
    uncapped value reaching the store aborts the whole batch — and a backstop whose failure mode
    is the thing it guards against is not a backstop. **R27's cap is unchanged and still owed by
    TASK-0005**; what is gone is the schema's redundant second copy of it.
    `amount` stays a plain `NUMERIC`; `Money` is a Java type an `AttributeConverter` maps onto it,
    so the schema knows
    nothing about the wrapper and no currency column exists. The three mirror the aggregate's
    rule that only identity is required (TASK-0003): a `NOT NULL` here would reject a real award
    over a field the source left blank, which is what #42 forbids for the amount and the date and
    what R7's *store what is published* forbids for the rest;
  - `operador_economico_id UUID REFERENCES operador_economico(id)` — **nullable**, plus an index on
    it. This single column **is** the awardee: the schema is normalised, so the name and fiscal
    identifier live once on `operador_economico` and no contract row repeats them. It is what
    TASK-0003's `@Relation(MANY_TO_ONE)` maps. Created here, with the table, rather than added
    later: it is the reason [FEAT-0010](../FEAT-0010-operadores-economicos-base/README.md)'s base
    lands first, since `ALTER`-ing it onto a table of millions and backfilling from re-derived data
    is a different operation entirely. Nothing in this feature ever writes it — [FEAT-0010
    TASK-0004](../FEAT-0010-operadores-economicos-base/TASK-0004-derivation-during-import.md) is the
    only thing that does, and SPEC-0006 R5 is why it stays nullable;
  - an index on `(organo_id, publication_date)` — what the browsing feature's
    mandatory year scoping reads on. It is created **here, empty**, because adding it later to
    a table of millions is a different operation from creating it now.
- The Micronaut Data JDBC implementation of `ContratoMenorRepository`.
- **The batch upsert**: one statement per batch,
  `INSERT … ON CONFLICT (source_id) DO UPDATE SET …` over every source-derived column,
  never delete-and-reinsert, so a re-imported contract keeps its UUID and its row. It must
  **distinguish inserted rows from updated ones** in what it returns (PostgreSQL exposes this
  as `xmax = 0` on the returned row); without that the added/refreshed counts R20 reports
  cannot be produced without a second read of the whole batch. The rows travel into it as
  parallel arrays through `unnest` rather than as a `VALUES` list assembled per batch, which
  keeps the statement a constant — one prepared form whatever the batch size, and no SQL built
  around a placeholder count. A page repeating a publication is collapsed to its last reading
  before the statement runs, because PostgreSQL refuses an `ON CONFLICT DO UPDATE` that would
  touch one row twice and that refusal is deterministic — one repeated row would fail identically
  on every retry and block that Órgano's history for good.
  `operador_economico_id` is written on insert and absent from the `DO UPDATE SET` **because
  nothing derives an awardee yet**, so a re-import carries none and the update has nothing
  truthful to write there. That is a consequence of the ordering, not a rule:
  [FEAT-0010 TASK-0004](../FEAT-0010-operadores-economicos-base/TASK-0004-derivation-during-import.md)
  resolves the awardee on *every* upsert precisely so a corrected fiscal identifier repoints the
  foreign key, and adding `operador_economico_id = EXCLUDED.operador_economico_id` to the update
  is that task's to make.
- `countByOrganoId` — a plain count on the indexed column.
- No delete path exists, in the port or the adapter.

## Acceptance criteria
- A contract inserted with a null id comes back carrying the generated `ContratoMenorId`, and
  reading it back yields the same value — the converter round-trips through
  `@GeneratedValue`, which is the mechanism TASK-0003 establishes and everything here relies on.
- Upserting a batch containing a source identifier already stored updates that row **in
  place** — same `id`, refreshed attributes — never inserting a second.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #17)
- A **direct insert bypassing the upsert** with an existing `source_id` fails at the
  unique constraint: the no-duplicates rule holds at the store even when use-case logic slips,
  which is the reason the constraint exists rather than only the upsert. (SPEC-0005 #17)
- Upserting the same batch twice leaves the stored set and every attribute unchanged, and
  reports the second run as all-refreshed, none-added. (SPEC-0005 #17)
- A batch mixing new and already-stored publications reports the two counts correctly — this
  is the number the run outcome states, so an off-by-one here is a wrong report, not a cosmetic
  slip. (SPEC-0005 #29, counts half)
- A contract stored by an earlier batch and absent from a later one is still present and
  unchanged afterwards — nothing in this adapter deletes. (SPEC-0005 #17)
- Published text round-trips unchanged through the store — the object at its published length,
  however long, and the duration as the adapter handed it over — an interpreted date round-trips
  as the same `LocalDate`, and a `Money` round-trips at its published scale without rounding,
  with a null date and null amount
  where the source gave nothing interpretable. (SPEC-0005 #40 storage half, less the date and the
  awardee, #42 stored-not-rejected half)
- The contract table holds **no awardee column**: reading a contract's awardee means joining
  `operador_economico`, and a contract with a null `operador_economico_id` is stored and readable
  like any other. (SPEC-0006 #8, no-operador half)
- `countByOrganoId` returns the stored count for one Órgano and is unaffected by another
  Órgano's contracts.
- A duration longer than the adapter's 64-character cap stores and reads back whole rather than
  failing — the column bounds nothing, so a value that slipped past the cap cannot abort a batch
  and reject a real award.
- Integration-tested against PostgreSQL (Testcontainers), including the unique-constraint and
  the mixed-batch count cases.
