---
feat: FEAT-0011
domain: backend
adrs: [0002, 0008, 0022]
status: todo
depends_on: [TASK-0001, TASK-0002]
---

# The four paged, ordered and counted reads

The `infrastructure` implementation of [TASK-0001](TASK-0001-selection-value-types-and-read-ports.md)'s
four ordering ports, written against the indexes
[TASK-0002](TASK-0002-visible-browse-schema-and-indexes.md) created. Four explicit statements,
each with its own count query, each total, each carrying the visibility predicate.

They are **four statements rather than one assembled ordering**, and that is the design's
decision, not this task's convenience: R19 fixes a closed set that cannot grow without the
requirement changing, a built `ORDER BY` is where an unindexed or non-total ordering slips in
unreviewed, and — for a native `@Query` — an interpolated property name is SQL injection. Four
statements are also four things a test can stand on.

## Scope

- `JdbcContratoMenorRepository` adds `VisibleContratoMenorRepository` to the interfaces it implements —
  the port [TASK-0001](TASK-0001-selection-value-types-and-read-ports.md) declared and nothing has
  implemented until now, joining `ContratoMenorRepository` and `OrganosWithVisibleContracts` on the
  same adapter.
- Four abstract methods on `JdbcContratoMenorRepository`, each `@Query(value = …, countQuery = …)`
  returning `Page<VisibleContratoMenor>`. Every one of them:
  - **filters identically**, and the predicate is the definition of *visible* rather than a filter
    bolted on:

    ```sql
    organo_id = :organoId
      AND publication_year = :year
      AND amount IS NOT NULL
      AND operador_economico_id IS NOT NULL
    ```

    The date needs no conjunct — `publication_year` is null exactly when `publication_date` is, so
    the equality test already excludes an undated contract. The other two are explicit, written
    the same way in all **eight** statements. The predicate is also exactly the two indexes'
    partial predicate, which is what lets PostgreSQL use them without re-checking off the heap.
  - **inner-joins `operador_economico`** for the awardee's name and canonical fiscal identifier.
    The join alone would already exclude a contract with no awardee, and
    `operador_economico_id IS NOT NULL` is written anyway: the predicate has to match the indexes'
    partial predicate word for word for PostgreSQL to use them.
  - **ends with the `source_id` tiebreaker in the direction of the key it breaks** —
    `ORDER BY publication_date DESC, source_id DESC`, not `…, source_id ASC`. Either is total, so
    correctness does not choose; the index does. A mixed direction is not the reverse of anything
    a B-tree holds and forces a sort, which
    [TASK-0002](TASK-0002-visible-browse-schema-and-indexes.md)'s `EXPLAIN` assertions will catch.
  - **declares its own `countQuery` carrying the same `WHERE` and the same join**, which
    [ADR-0022](../../architecture/0022-paged-collection-contract-from-micronaut-data.md) requires
    of every explicit `@Query` returning a `Page` — annotation processing fails without one, and a
    count that dropped a conjunct is a total that disagrees with the pages beneath it.
  - **takes its `Pageable` as given** and adds no `ORDER BY` from it. The framework appends a
    sorted `Pageable`'s ordering to a `@Query` that already has one and emits two;
    [TASK-0005](TASK-0005-list-contratos-menores-use-case.md) is what guarantees none arrives.
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
- The four statements' `WHERE` and `ORDER BY` stay **byte-identical** to the SQL
  [TASK-0002](TASK-0002-visible-browse-schema-and-indexes.md)'s `EXPLAIN` test pins. If one has to
  change, both change together.
- Integration tests against PostgreSQL, in `server/infrastructure/src/integrationTest`:
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

## Acceptance criteria

- Paging a selection exhaustively in each of the four orderings yields exactly `totalSize`
  contracts, none repeated and none skipped, with ties present on the sorted value.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #23, store half)
- Each ordering is total and ends with `source_id` in the direction of its key; a descending sort
  ends `source_id DESC`. (SPEC-0005 #42, ordering half)
- A read of one year returns only that year's contracts, and a contract on either year boundary
  appears in exactly one year. (SPEC-0005 #27, year-scoping half)
- Every page of a selection reports the count of the whole selection rather than of the page, and
  a page beyond the last is empty and still reports it. (SPEC-0005 #28)
- A contract missing its publication date, its amount, its awardee, or all three is **absent from
  every page and from every count** in all four orderings, and is still stored — a direct read by
  `source_id` finds it. (SPEC-0005 #50, query half)
- No statement builds an `ORDER BY` from a parameter, and no method accepts a `Sort`, a property
  name or a direction. (SPEC-0005 #28)
- Each `@Query` declares a `countQuery` whose `WHERE` and join match its own.
