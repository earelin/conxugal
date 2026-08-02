---
feat: FEAT-0009
domain: backend
adrs: [0002, 0008, 0019]
status: todo
depends_on: [TASK-0003]
---

# Contratos menores store: migration + JDBC repository

The schema and driven adapter behind [TASK-0003](TASK-0003-contrato-menor-domain-model.md)'s
port. **Prerequisite outside this feature:**
[FEAT-0010 TASK-0003](../FEAT-0010-operadores-economicos-base/TASK-0003-operador-store.md)
creates the `operador` table this one's foreign key points at. Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md) and
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md);
JDBC and SQL stay entirely in `infrastructure`.

## Scope
- A migration (next free `V` number) creating `contrato_menor`:
  - `id UUID PRIMARY KEY` — the column is a plain `uuid`; `ContratoMenorId` is a Java type that
    an `AttributeConverter` maps onto it
    ([ADR-0019](../../architecture/0019-typed-aggregate-identifiers.md)), so nothing in the
    schema knows about the wrapper;
  - `publication_id BIGINT NOT NULL UNIQUE` — R12's "no duplicates" enforced **at the store**,
    so it holds even if use-case logic slips, exactly as `source_key` does for the catalogue;
  - `organo_id UUID NOT NULL REFERENCES organo_contratacion(id)`;
  - `publication_date DATE` — nullable, and the **only** date column: the source's `DD-MM-YYYY`
    text is interpreted at the adapter and not stored (TASK-0003 records what that costs against
    R27);
  - `objeto TEXT`, `amount NUMERIC`, `duration TEXT` — **nullable**, mirroring the aggregate's
    rule that only identity is required (TASK-0003): a `NOT NULL` here would reject a real award over a field the
    source left blank, which is what #42 forbids for the amount and the date and what R7's
    *store what is published* forbids for the rest;
  - `operador_id UUID REFERENCES operador(id)` — **nullable**, plus an index on it. This single
    column **is** the awardee: the schema is normalised, so the name and fiscal identifier live
    once on `operador` and no contract row repeats them. It is what TASK-0003's
    `@Relation(MANY_TO_ONE)` maps. Created
    here, with the table, rather than added later: this column is the reason
    [FEAT-0010](../FEAT-0010-operadores-economicos-base/README.md)'s base lands first, since
    `ALTER`-ing it onto a table of millions and backfilling from re-derived data is a different
    operation entirely. Nothing in this feature ever writes it —
    [FEAT-0010 TASK-0004](../FEAT-0010-operadores-economicos-base/TASK-0004-derivation-during-import.md)
    is the only thing that does, and SPEC-0006 R5 is why it stays nullable;
  - an index on `(organo_id, publication_date)` — what the browsing feature's
    mandatory year scoping reads on. It is created **here, empty**, because adding it later to
    a table of millions is a different operation from creating it now.
- The Micronaut Data JDBC implementation of `ContratoMenorRepository`.
- **The batch upsert**: one statement per batch,
  `INSERT … ON CONFLICT (publication_id) DO UPDATE SET …` over every source-derived column,
  never delete-and-reinsert, so a re-imported contract keeps its UUID and its row. It must
  **distinguish inserted rows from updated ones** in what it returns (PostgreSQL exposes this
  as `xmax = 0` on the returned row); without that the added/refreshed counts R20 reports
  cannot be produced without a second read of the whole batch.
- `countByOrganoId` — a plain count on the indexed column.
- No delete path exists, in the port or the adapter.

## Acceptance criteria
- A contract inserted with a null id comes back carrying the generated `ContratoMenorId`, and
  reading it back yields the same value — the converter round-trips through
  `@GeneratedValue`, which is the mechanism TASK-0003 establishes and everything here relies on.
- Upserting a batch containing a publication identifier already stored updates that row **in
  place** — same `id`, refreshed attributes — never inserting a second.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #17)
- A **direct insert bypassing the upsert** with an existing `publication_id` fails at the
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
  the duration as published — and an interpreted date round-trips as the same `LocalDate`, with a
  null date and null amount where the source gave nothing interpretable. (SPEC-0005 #40 storage
  half, less the date and the awardee, #42 stored-not-rejected half)
- The contract table holds **no awardee column**: reading a contract's awardee means joining
  `operador`, and a contract with a null `operador_id` is stored and readable like any other.
  (SPEC-0006 #8, no-operador half)
- `countByOrganoId` returns the stored count for one Órgano and is unaffected by another
  Órgano's contracts.
- Integration-tested against PostgreSQL (Testcontainers), including the unique-constraint and
  the mixed-batch count cases.
