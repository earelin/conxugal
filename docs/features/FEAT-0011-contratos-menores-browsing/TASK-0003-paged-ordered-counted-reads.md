---
feat: FEAT-0011
domain: backend
adrs: [0002, 0008, 0022]
status: done
depends_on: [TASK-0001, TASK-0002]
---

# The paged, ordered and counted read

The `infrastructure` implementation of [TASK-0001](TASK-0001-selection-value-types-and-read-ports.md)'s
browse port, written against the indexes
[TASK-0002](TASK-0002-visible-browse-schema-and-indexes.md) created. **One statement with its own
count query, carrying the visibility predicate and no ordering at all.**

The ordering is not here, and that is the design's decision rather than this task's convenience:
the four orderings arrive as the `Sort` on the `Pageable`, built by
[TASK-0005](TASK-0005-list-contratos-menores-use-case.md) from the closed set of `SortKey` and
`Sort.Order.Direction` values, and the framework appends it to the end of this native statement. A
statement that ordered as well would emit two `ORDER BY` clauses and fail.

## Scope

- `JdbcContratoMenorRepository` adds `VisibleContratoMenorRepository` to the interfaces it
  implements — the port [TASK-0001](TASK-0001-selection-value-types-and-read-ports.md) declared and
  nothing has implemented until now, joining `ContratoMenorRepository` and
  `OrganosWithVisibleContracts` on the same adapter.
- One abstract `page` method, `@Query(value = …, countQuery = …)` returning
  `Page<VisibleContratoMenor>`. It:
  - **filters on the definition of *visible*** rather than a filter bolted on:

    ```sql
    organo_id = :organoId
      AND publication_year = :year
      AND amount IS NOT NULL
      AND operador_economico_id IS NOT NULL
    ```

    The date needs no conjunct — `publication_year` is null exactly when `publication_date` is, so
    the equality test already excludes an undated contract. The other two are explicit, written
    the same way in **both** statements. The predicate is also exactly the two indexes' partial
    predicate, which is what lets PostgreSQL use them without re-checking off the heap.
  - **inner-joins `operador_economico`** for the awardee's name and canonical fiscal identifier.
    The join alone would already exclude a contract with no awardee, and
    `operador_economico_id IS NOT NULL` is written anyway: the predicate has to match the indexes'
    partial predicate word for word for PostgreSQL to use them.
  - **carries no `ORDER BY`.** The `Pageable`'s `Sort` supplies it, appended at the end of the SQL
    by `DefaultSqlPreparedQuery.attachPageable`.
  - **declares its own `countQuery` carrying the same `WHERE` and the same join**, which
    [ADR-0022](../../architecture/0022-paged-collection-contract-from-micronaut-data.md) requires
    of every explicit `@Query` returning a `Page` — annotation processing fails without one, and a
    count that dropped a conjunct is a total that disagrees with the pages beneath it.
- **Verify how a `Sort` order names its column on a native statement.** The appended `ORDER BY` has
  to say `source_id`, not `sourceId`. Micronaut's default naming strategy is underscore-separated
  lowercase and `ContratoMenor` carries all three properties, so the translation is expected — but
  it is *expected*, not proven, and this task is where it gets proven. If the property name is
  appended verbatim instead, the orders name the columns directly; either way the emitted SQL is
  asserted rather than assumed.
- `VisibleContratoMenor`'s `Money` and `FiscalIdentifier` components map through the `@TypeDef`
  converters already on those types, by two different routes:
  [TASK-0001](TASK-0001-selection-value-types-and-read-ports.md) records that `amount` inherits its
  mapping from `ContratoMenor`'s own `Money` property while `awardeeFiscalId`, joined in rather
  than held on the contract, is rebuilt by the `TypeConverter` half that task added. **The
  integration tests below assert both values on a returned row**, since a converter that failed to
  apply is the failure mode this arrangement has. If DTO projection turns out not to carry a
  converted component at all, the fallback is a hand-written mapping through
  `jdbcOperations.prepareStatement` in this same class — the precedent the batch upsert already
  sets — rather than weakening the projection to raw `BigDecimal` and `String`.
- The statement's `WHERE` stays **byte-identical** to the SQL
  [TASK-0002](TASK-0002-visible-browse-schema-and-indexes.md)'s `EXPLAIN` test pins, and the four
  orderings that test pins are now produced by building the same four `Sort` values this task's
  tests use. If one has to change, both change together.
- Integration tests against PostgreSQL, in `server/infrastructure/src/integrationTest`:
  - **the emitted `ORDER BY` is asserted for each of the four orderings**, ending with `source_id`
    in the direction of the key it breaks — the invariant that used to be visible in four
    statements and is now a property of what the caller builds;
  - **exhaustive paging over a selection with ties** — hundreds of contracts on one publication
    date and repeated round amounts — walked page by page in all four orderings, yielding exactly
    the stated count with **none repeated and none skipped**;
  - **a year boundary**: a contract published on 1 January and one on 31 December belong to their
    own year and leak into neither neighbour;
  - **the `Page`'s total is the whole selection**, not the page returned — a year of 120 contracts
    read at `size = 50` answers `totalSize = 120` on every page;
  - **a page past the end** answers an empty page carrying the true total;
  - **withholding**: a stored contract missing its date, one missing its amount, one missing its
    awardee, and **one missing all three** appear in no page and in **no count**, in all four
    orderings. The last is what catches a `countQuery` that dropped a conjunct.

## What building it found

> **Amended.** Two of the scope's expectations did not survive contact with Micronaut Data 5.0.4,
> and both were branches this task existed to settle. The design is unchanged — one statement, one
> count over the same predicate, no ordering of its own, the ordering arriving on the `Pageable`
> from the closed set — and the acceptance criteria below are met as written, bar the last, which
> named a mechanism rather than a property.
>
> - **A `Sort` reaches a native statement verbatim only if the `@Query` says `nativeQuery = true`.**
>   Left at its default a `@Query` on a JDBC repository is *not* native for this purpose:
>   `AbstractSqlLikeQueryBuilder.buildPropertyByName` resolves each name against `ContratoMenor`
>   and appends the column behind it **qualified by the alias it would itself have given the
>   table** — a name a hand-written statement never introduces, so PostgreSQL refuses it. So the
>   answer to the scope's open question is *verbatim*, and `SortKey` names columns.
> - **A DTO projection cannot carry a converted component at all**, which is the fallback the scope
>   anticipated. Micronaut Data builds a projection's mapping from a `RuntimePersistentEntity`
>   constructed outside the container (`AbstractSqlRepositoryOperations.resolveDtoPersistentEntity`),
>   whose `resolveConverter` throws *Converters not supported*. Both of `VisibleContratoMenor`'s
>   converted components hit it — the amount because it inherits `ContratoMenor`'s mapping, exactly
>   as [TASK-0001](TASK-0001-selection-value-types-and-read-ports.md) says it does. So the rows are
>   read through `jdbcOperations.prepareStatement`, the precedent the batch upsert sets, rather than
>   the projection being weakened to raw `BigDecimal` and `String`.
>
> Reading by hand means the adapter appends the ordering rather than the framework, and that is
> where the two meet: appending a name to SQL is the one thing this design cannot do carelessly, so
> the adapter **refuses any column `SortKey` could not have produced**, deriving that set from
> `SortKey` itself. The closed set is now a property of the statement rather than only an
> obligation on its callers — strictly more than the framework path offered, which interpolates
> whatever the `Sort` carries.
>
> **The count does not join, and that is not a shortcut.** Sharing one whole `FROM … WHERE` between
> the page and the count reads as the tidier way to keep them honest, and it is wrong: the join is
> a no-op for a `COUNT(*)` — `operador_economico_id` references a primary key and the predicate
> already excludes the null — but it takes the planner off the partial index onto a heap scan of
> every candidate row plus a scan of the whole operador table, roughly doubling the work on every
> page a reader turns. So the **`WHERE` is shared and the `FROM` is not**, and
> [TASK-0002](TASK-0002-visible-browse-schema-and-indexes.md)'s `EXPLAIN` of the count is now the
> statement this read actually emits, asserted character for character rather than in spirit.
>
> The page's own join is harmless: it keeps its index scan in all four orderings. Only at offsets in
> the tens of thousands does the plan fall back to sorting the selection, which is offset paging's
> behaviour rather than this join's — the same statement without the join does the same thing.
>
> The two statements share a connection but not a snapshot: `READ COMMITTED` gives each its own, so
> an import committing between them can leave a total one row from the pages beneath it. Writing
> the predicate once removes the *systematic* disagreement — a dropped conjunct — and not this one,
> which for a browse list is a page that shifts on refresh.
>
> The ordering itself moved into `SortKey.ordering(Direction)`, in `domain`. The scope left it to
> [TASK-0005](TASK-0005-list-contratos-menores-use-case.md), but three places need the same four
> orderings — that use case, this task's tests, and TASK-0002's `EXPLAIN` test — and a mapping from
> a key to a **column name** is the one thing that must not be written twice.

## Acceptance criteria

- Paging a selection exhaustively in each of the four orderings yields exactly `totalSize`
  contracts, none repeated and none skipped, with ties present on the sorted value.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #23, store half)
- Each of the four orderings is total and ends with `source_id` in the direction of its key; a
  descending sort ends `source_id DESC`, asserted against the SQL actually emitted.
  (SPEC-0005 #42, ordering half)
- A read of one year returns only that year's contracts, and a contract on either year boundary
  appears in exactly one year. (SPEC-0005 #27, year-scoping half)
- Every page of a selection reports the count of the whole selection rather than of the page, and
  a page beyond the last is empty and still reports it. (SPEC-0005 #28)
- A contract missing its publication date, its amount, its awardee, or all three is **absent from
  every page and from every count** in all four orderings, and is still stored — a direct read by
  `source_id` finds it. (SPEC-0005 #50, query half)
- The statement carries no `ORDER BY` of its own, and no property name reaching the emitted
  `ORDER BY` comes from anywhere but a `SortKey` or a `Sort.Order.Direction`. (SPEC-0005 #28)
- The count carries the same `WHERE` as the page — met by both being built around the same
  constant, rather than by a `countQuery` kept in step with a `@Query`. It carries **no join**: the
  page's join is a no-op for a count and costs it the index-only scan
  [TASK-0002](TASK-0002-visible-browse-schema-and-indexes.md) exists to provide. See the amendment
  above.
