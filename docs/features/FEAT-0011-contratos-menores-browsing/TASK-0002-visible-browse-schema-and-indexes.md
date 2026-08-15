---
feat: FEAT-0011
domain: backend
adrs: [0002]
status: done
depends_on: [TASK-0001]
---

# The schema the browse reads need: a stored `publication_year` and two partial indexes

One migration adding the generated year column every browse read filters on, creating the two
composite indexes the four orderings, both counts and the year facets are served by, and dropping
the index [FEAT-0009](../FEAT-0009-contratos-menores-initial-import/README.md)'s
[TASK-0004](../FEAT-0009-contratos-menores-initial-import/TASK-0004-contratos-menores-store.md)
created, which the first of them subsumes.

**It lands before the queries on purpose.** They are written against these indexes, an `EXPLAIN`
assertion is only meaningful once both exist, and the feature's timing argument is that the cheap
moment to create an index on this table is now — before it holds millions — not after R24's
measurement proves it was needed.

## Scope

- A migration `V16` in `server/infrastructure/src/main/resources/db/migration/`:
  - **`publication_year` as a stored generated column** over `publication_date`:

    ```sql
    ALTER TABLE contrato_menor
        ADD COLUMN publication_year INTEGER
            GENERATED ALWAYS AS (EXTRACT(YEAR FROM publication_date)::int) STORED;
    ```

    The cast is not decoration: `EXTRACT` answers `numeric` in current PostgreSQL, and an
    `INTEGER` generated column will not accept it without one. Being generated is what makes the
    column unable to disagree with the date it comes from, and what means **no import writes it**
    — it is null exactly when `publication_date` is, which is how an equality test on it withholds
    every undated contract without naming one.
  - **Two composite indexes, both partial** on the visibility predicate:

    ```sql
    CREATE INDEX contrato_menor_organo_year_date_idx
        ON contrato_menor (organo_id, publication_year, publication_date, source_id)
        WHERE amount IS NOT NULL AND operador_economico_id IS NOT NULL;

    CREATE INDEX contrato_menor_organo_year_amount_idx
        ON contrato_menor (organo_id, publication_year, amount, source_id)
        WHERE amount IS NOT NULL AND operador_economico_id IS NOT NULL;
    ```

    The first serves date ascending, date descending as a backward scan, every `COUNT`, the year
    facets as an index-only scan, and
    [FEAT-0012](../FEAT-0012-organos-visible-set-and-browse/README.md)'s *does this Órgano hold a
    visible contrato menor*. The second serves both amount directions — descending as a plain
    backward scan, which is R24's named read. **One index covers both amount directions** because
    R28 leaves no null amount in the visible set to place, so no `NULLS LAST` is needed and no
    second index with it.
  - **A third index, whole**, `(organo_id)`. Added on review, because the claim that the first
    index "answers everything the old one did" is false, and the difference is *partial*.
    `countByOrganoId` — the import's per-window completion check — counts an Órgano's contracts
    **whole**, anomalies included, since it is compared against the total the source publishes. No
    partial index can serve a count of every row, so without this one that read becomes a
    sequential scan of a table headed for millions, run once per window of every walk. Measured at
    1.4M rows: a 13-buffer index-only scan becomes a 22 967-buffer parallel sequential scan.
  - **Dropping `contrato_menor_organo_id_publication_date_idx`**, which the three above jointly
    answer everything of — the browse reads take the first, the completion count takes the last,
    and its `publication_date` column is what neither of them needed from it.
  - **A `lock_timeout`**, because adding a stored generated column rewrites the table under
    `ACCESS EXCLUSIVE` and Flyway runs at boot: a deploy meeting a long-running import would
    otherwise hold every reader and writer behind it, unbounded. Failing the boot is the better of
    the two bad outcomes.
  - **`contrato_menor_operador_economico_id_idx` stays** exactly as `V13` created it: it serves
    the foreign key and, later, SPEC-0006's operador history.
- **No entity change.** `publication_year` is not a component of `ContratoMenor` and must not
  become one: nothing writes it, nothing reads it through the aggregate, and a derived value on an
  aggregate is a second copy of a fact that already has one. The existing batch upsert must keep
  naming its columns explicitly — a generated column cannot be inserted into.
- The migration comments only the non-obvious: the `::int` cast, and what the partial predicate
  buys. It does not restate the column list.
- An integration test beside `ContratoMenorMigrationIntegrationTest`, against PostgreSQL:
  - the column exists, is `INTEGER`, is generated stored, and holds the year of an existing row's
    `publication_date` and null for a row without one, with no backfill statement of its own;
  - both partial indexes exist with the predicate, the whole one exists, and the old index is gone;
  - an `EXPLAIN` of **each of the four orderings, their count, the year-facet read, the import's
    completion count and the visible-set semi-join** — written as the literal SQL each carries,
    over `contrato_menor` alone — shows the intended index, **no sort node**, and **no heap fetch**
    where the read is meant to reach none. That last is what the partial predicate buys and what a
    later widening of either index would silently lose.
  - the year-facet read is asserted on its **result** as well as its plan: it offers the years the
    Órgano has visible contracts in and **nothing else**, which no plan assertion can see and which
    a seeded contract with an amount, an awardee and no date would otherwise break.
  - The plans are taken after seeding rows across two Órganos and two years and running
    `VACUUM (ANALYZE) contrato_menor`, with `SET LOCAL enable_seqscan = off` and
    `enable_bitmapscan = off`. `ANALYZE` alone gives the planner statistics but only `VACUUM` sets
    the visibility map, without which an index-only scan still reports heap fetches; and both scan
    types are ways of reading a whole selection and sorting it afterwards, which on a fixture this
    size is genuinely cheaper — leaving either on measures the fixture's row count rather than the
    schema. What the flags leave the planner is the choice under test: read one index in order, or
    read one and sort. The assertion is about **what the index can serve**.

## Acceptance criteria

- `publication_year` exists as a stored generated `INTEGER` column, equals the year of
  `publication_date` for every row that has one, is null for every row that does not, and no
  statement in the codebase writes it. (SPEC-0005 #27)
- Both composite indexes exist with the predicate
  `amount IS NOT NULL AND operador_economico_id IS NOT NULL`, the whole `(organo_id)` index exists,
  and `contrato_menor_organo_id_publication_date_idx` no longer exists. (SPEC-0005 #37)
- `EXPLAIN` of each of the four orderings shows the intended partial index and **no sort node** —
  including amount descending, served as a backward scan of the amount index. (SPEC-0005 #37, #42)
- `EXPLAIN` of the selection's count query shows a partial index scan and no heap access beyond it.
- `EXPLAIN` of `SELECT DISTINCT publication_year …` over one Órgano shows an **index-only scan**
  with no heap fetch, and the read answers **only years** — never the null a dated-less but
  otherwise complete contract would contribute. (SPEC-0005 #43, #50)
- `countByOrganoId`, which counts an Órgano's contracts whole and which no partial index can serve,
  still reaches an index rather than scanning the table.
- The visible-set semi-join FEAT-0012 shipped is answered by the partial date index as an
  index-only scan with no heap fetch.
- The migration applies to a database already holding contratos menores, and the existing suite —
  including FEAT-0009's import integration tests and the batch upsert — passes unchanged against
  the new schema.
- `scripts/docs-lint.sh` passes for the docs touched, and the migration carries no comment
  restating its own column names or types.
