---
feat: FEAT-0016
domain: backend
adrs: [0008]
status: todo
depends_on: [TASK-0001]
---

# The visible-browse schema: a year column, one index, and the collation V19 deferred

The schema the browse reads need.
[V19](../../../server/infrastructure/src/main/resources/db/migration/V19__create_licitacion_and_award_points.sql)
created the licitación tables with **no `publication_year`, no browse index and no collation on any
text column**, deferring all three in as many words: *"the reference lists a reader narrows by are the
browsing feature's… The browsing feature measures its own queries and adds what they ask for"*, and
it *"adds the collation with the read that needs it"*. This is that feature and this is that task.

The migration takes the **next free `V` across `db/migration` and `db/migration-local`** — the two
share one sequence — settled at merge time rather than fixed here.

## Scope

- **`publication_year`**, a **stored** generated column, `EXTRACT(YEAR FROM publication_date)::int`.
  Generated so it cannot disagree with the date it derives from and no import writes it; and null
  exactly when the date is, which is what makes the year equality withhold every undated procedure
  (R25) without naming one.

  ❗ **`STORED` must be spelled explicitly.** A generated column that is not stored **cannot be
  indexed**, and the index below is the only reason this column exists.
- **One index**, partial on the visibility rule:

  ```sql
  CREATE INDEX licitacion_organo_year_date_idx
      ON licitacion (organo_id, publication_year, publication_date, publication_id)
      WHERE withdrawn = FALSE;
  ```

  It carries **no `DESC`**: the tie-break takes the direction of the key it breaks
  ([TASK-0001](TASK-0001-selection-value-types-and-read-ports.md)), so the descending default is a
  plain backward scan and the ascending order a forward one. The partial predicate must match the
  browse predicate's `withdrawn = FALSE` **conjunct for conjunct**, or PostgreSQL cannot use it.

  It serves the two date orderings, the selection's `COUNT(*)`, the year facets, and
  [TASK-0006](TASK-0006-licitacions-in-the-visible-set.md)'s semi-join. **It serves neither amount
  ordering, and nothing can** — that key is an aggregate over `licitacion_award`, and no B-tree,
  expression index or partial index on this table can produce it. The feature README argues why that
  is affordable here and was not for contratos menores; this task's job is to **prove the claim
  rather than assert it**.
- **`licitacion_state.label COLLATE "galician"`**, matching `operador_economico.name`. The state
  chooser orders labels ([TASK-0004](TASK-0004-year-cpv-and-state-facets.md)); under the cluster
  default every accented Galician label sorts after `Z`, which no ASCII fixture would reveal.
- **`SET LOCAL lock_timeout`**, on V16's reasoning rather than its numbers. Adding a stored generated
  column rewrites the table under `ACCESS EXCLUSIVE`; at this table's size the rewrite is
  instantaneous and the risk is **what it might queue behind**. Flyway runs at boot, and this
  family's initial import is the longest sustained outbound stream the system produces (R31) — a
  deploy landing during one would close the service for as long as that import takes.
- **Correction 1**, in this migration's header: `licitacion.publication_id` **is ordered now**, by
  every read a reader can reach, where V19's own comment says it never is — and this index is the fact
  that makes it so. It records the reading taken (lexicographic, not numeric, among identifiers of
  differing digit count) and its bound.

  ❗ **It goes here because V19 may not be edited.** Its checksum is recorded in every
  `flyway_schema_history` that has applied it, and V17's header refuses the same edit for the same
  reason. This is V17's own shape: a later migration records what an earlier comment can no longer
  say. [TASK-0008](TASK-0008-correct-the-two-v19-comments.md) carries the other correction and states
  the whole argument.

  ❗ **Do not delete V19's middle clause along with the false one.** The comment reads "*never ordered,
  summed or incremented -- **the walk resumes by the order the listing endpoint applies at the
  source** -- so text gives up nothing here*". The emphasised clause is the comment's actual reason
  and is **still true**; only *never ordered* has been overtaken. Nothing in V19 changes, but the
  header written here must not imply the whole comment is wrong.

**Out of scope:** any statement that uses the index — [TASK-0003](TASK-0003-paged-ordered-counted-reads.md)
and TASK-0004 own those — and **dropping `licitacion_organo_id_idx`**, which is kept.

**V16's history reads the other way round from what an earlier draft of this task said.** It
**created** `contrato_menor_organo_id_idx` — a *whole* index on `organo_id`, deliberately, because the
import's completion count reads an Órgano's contracts including the anomalies the partial indexes
exclude — and **dropped** the older `contrato_menor_organo_id_publication_date_idx`, whose date column
served nobody. So the precedent is *keep a whole index when something counts the table whole*, not
*drop it once the partial ones arrive*. Nothing counts licitacións by Órgano today, so this family has
no such read; the index is kept because at this size the write cost is nil and dropping one nothing
has asked for is the more expensive mistake.

**No column for the amount, and none for the awardee count.** Both are aggregates over other tables,
so neither can be a generated column at all, and a trigger-maintained one would have to be kept in
step by five separate writes. The feature README states the argument; this task must not quietly
introduce one to make an ordering indexable.

## Acceptance criteria

- A migration integration test pins `licitacion`'s exact column set with `containsExactlyInAnyOrder`,
  on `ContratoMenorMigrationIntegrationTest`'s precedent, and asserts `publication_year` is
  `INTEGER`, generated and **stored**.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #32)
- A licitación stored with a publication date reads back a `publication_year` equal to that date's
  year; one stored with **no** date reads back a null year. No insert or update writes the column.
  A licitación published on **1 January** belongs to that year and to no other. (SPEC-0008 #32, #36)
- `EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF)` over the **default ordering** and over its
  ascending twin shows the partial index used and **no sort node**, on
  `ContratoMenorVisibleBrowseSchemaIntegrationTest`'s precedent. (SPEC-0008 #30)
- The same over the selection's `COUNT(*)` and over the **year facets** shows an **index-only** scan
  with no heap fetch. (SPEC-0008 #28, #32)
- **The honest negative is a test, not prose**: the same over **both amount orderings** shows a sort
  node, and the test says so by name. A later change that made them index-ordered would fail this
  test and force whoever made it to revisit the feature's argument rather than silently invalidating
  it. (SPEC-0008 #34)
- A withdrawn licitación is **absent from the index**: an `EXPLAIN` of the browse predicate over a
  fixture whose rows are all withdrawn touches no heap. (SPEC-0008 #18 read half)
- Two `licitacion_state` labels differing only by an accent order as Galician rather than as bytes —
  `Órgano`-style accented labels sort among their unaccented neighbours, not after `Z`.
  (SPEC-0008 #33)
- The migration sets a `lock_timeout` and **fails** rather than blocking when a conflicting lock is
  held, asserted the way V16's is.
