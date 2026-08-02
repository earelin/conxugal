---
feat: FEAT-0009
domain: backend
adrs: [0002, 0008]
status: todo
depends_on: [TASK-0003]
---

# Contratos menores store: migration + JDBC repository

The schema and driven adapter behind [TASK-0003](TASK-0003-contrato-menor-domain-model.md)'s
port. Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md) and
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md);
JDBC and SQL stay entirely in `infrastructure`.

## Scope
- A migration (next free `V` number) creating `contrato_menor`:
  - `id UUID PRIMARY KEY`;
  - `publication_id BIGINT NOT NULL UNIQUE` — R12's "no duplicates" enforced **at the store**,
    so it holds even if use-case logic slips, exactly as `source_key` does for the catalogue;
  - `organo_id UUID NOT NULL REFERENCES organo_contratacion(id)`;
  - `publication_date TEXT NOT NULL`, `publication_date_interpreted DATE` (nullable);
  - `objeto TEXT`, `amount NUMERIC`, `duration TEXT`, `awardee_name TEXT`,
    `awardee_fiscal_id TEXT` — **nullable**, mirroring the aggregate's rule that only identity
    is required (TASK-0003): a `NOT NULL` here would reject a real award over a field the
    source left blank, which is what #42 forbids for the amount and the date and what R7's
    *store what is published* forbids for the rest;
  - an index on `(organo_id, publication_date_interpreted)` — what the browsing feature's
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
- Published values round-trip unchanged through the store: padded fiscal identifier and awardee
  name, published date text, and a null interpreted date and null amount where the source gave
  nothing interpretable. (SPEC-0005 #40 storage half, #42 storage half)
- `countByOrganoId` returns the stored count for one Órgano and is unaffected by another
  Órgano's contracts.
- Integration-tested against PostgreSQL (Testcontainers), including the unique-constraint and
  the mixed-batch count cases.
