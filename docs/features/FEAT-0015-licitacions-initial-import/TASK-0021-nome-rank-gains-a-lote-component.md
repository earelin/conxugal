---
feat: FEAT-0015
domain: backend
adrs: [0008, 0023]
status: todo
depends_on: [TASK-0011]
---

# `NomeRank` gains a lote component

SPEC-0006 R4 breaks a name tie by *"the higher contract identifier"*, and `NomeRank` is
`(date, sourceId)` over one `BIGINT`. That is total for contratos menores and **not** for
licitacións: SPEC-0006 records a licitación's contract identity as a publication identifier
**together with a lote**, so two lotes of one procedure awarded to the same operador under two
published spellings tie **exactly** — same date, same identifier — `outranks` answers false in both
directions, and the displayed name falls to whichever row was written last. SPEC-0006 #36 asserts
that choice is deterministic *by construction*, so this is a defect rather than an untidiness.

**This is a migration on two shipped, populated tables, not a record's arity.** It was buried inside
[TASK-0011](TASK-0011-extract-resolve-operador.md) in an earlier draft, which named none of the
below and whose own first criterion — *every existing test passes unchanged* — cannot survive
changing the arity of a record that the test suite constructs in dozens of places. It is its own
task for that reason.

Under [ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md),
`NomeRank` is an `@Embeddable` that maps its own columns, so widening it is a schema change.

## Scope

- **A migration** (next free `V` across `db/migration` **and** `db/migration-local`, taken at merge
  time) adding the lote component to **both** tables `V12` gave the embeddable:
  - `operador_economico` — beside `name_rank_date` and `name_rank_source_id`;
  - `operador_economico_nome_alternativo` — beside `last_published_date` and
    `last_published_source_id`.
- **The column is `NOT NULL` with a backfilled constant**, and that is not a style preference. The
  rank comparison is duplicated in **SQL** as a PostgreSQL row-value, in
  `JdbcOperadorRepository.RETAIN_NAME`:

  ```sql
  WHERE (COALESCE(EXCLUDED.last_published_date, '-infinity'::date),
         EXCLUDED.last_published_source_id)
      > (COALESCE(operador_economico_nome_alternativo.last_published_date, '-infinity'::date),
         operador_economico_nome_alternativo.last_published_source_id)
  ```

  A `NULL` anywhere in a row-value comparison makes the predicate `UNKNOWN`, so a nullable new
  component would make that `DO UPDATE … WHERE` silently stop firing and retained names would stop
  advancing — a permanent, silent data defect of exactly the kind this task exists to prevent.
- **Both SQL statements widen with it** — `RETAIN_NAME` and `PROMOTE_NAME` — so the Java rule and
  the SQL rule stay the same rule. A divergence here is invisible until a name displays wrongly.
- **`NomeRank` takes the component** and `compareTo` orders on it after the source identifier, with
  the existing nulls-first date ordering and strict-win `outranks` untouched.
- **Contratos menores supply a constant.** They have no lotes and never will, so their ordering is
  unchanged and the tuple stays total for both families. A per-family discriminator was rejected:
  the two families share one publication id space (measured), so there is nothing to disambiguate
  except the lote itself.
- The call sites that construct a `NomeRank` move with the arity. This is mechanical and the
  compiler finds all of them; it is named so the size of the change is not a surprise.

**Out of scope:** resolving any licitacións bidder or awardee — the first caller that supplies a
real lote is [TASK-0022](TASK-0022-resolve-the-bidders.md).

## Acceptance criteria

- Two ranks differing **only** in their lote component order deterministically, in both directions,
  and neither `outranks` the other when they are equal.
  ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #36)
- **A contrato menor's rank is unchanged**: two contratos menores that ordered one way before order
  the same way after, since both carry the constant. (SPEC-0006 #36)
- After the migration, **retained-name advancement still works**: importing a further contract under
  a name an operador already retains advances that entry's date and identifier and adds no second
  entry — the `RETAIN_NAME` row-value comparison still fires. This is the criterion that catches a
  nullable column. (SPEC-0006 #34)
- Promotion still displaces and retains correctly after the widening. (SPEC-0006 #33)
- A migration integration test pins both tables' exact column sets, and every pre-existing row reads
  back with the backfilled constant.
- Integration-tested against PostgreSQL (Testcontainers), because two of these criteria are about
  SQL that no unit test exercises.
