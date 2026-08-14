---
feat: FEAT-0011
domain: backend
adrs: [0002, 0022]
status: todo
depends_on: [TASK-0001, TASK-0003]
---

# `ListContratosMenores`

The domain read answering one Órgano's contracts of one year, in one ordering, one page at a
time, with the count of the whole selection. It is where a validated sort key and direction
become **one of four fixed statements**, and it is the last place a `Sort` could reach a query —
so it is the place that guarantees none does.

## Scope

- `ListContratosMenores` in `gal.conxugal.domain.contrato`, taking an `OrganoId`, a
  `YearSelection`, a `SortKey`, a `SortDirection` and a `Pageable`, and answering
  `Page<VisibleContratoMenor>`.
- **The mapping is a total function over a closed set**: the four `(SortKey, SortDirection)` pairs
  select the four repository methods
  [TASK-0003](TASK-0003-paged-ordered-counted-reads.md) implements. It takes no default branch and
  no fallback — every pair is a case, because both types are enums and the compiler can see the
  set is covered.
- **It calls the repository with `pageable.withoutSort()`.** The controller already builds an
  unsorted `Pageable`
  ([ADR-0022](../../architecture/0022-paged-collection-contract-from-micronaut-data.md) binds none
  from the request), so this strips nothing today. It is written anyway, and the reason is stated
  in the javadoc: **no `Sort` ever reaches these statements**. They are native queries, an
  unvalidated property name would be interpolated into `ORDER BY` verbatim, and a `@Query` that
  already orders would emit a second `ORDER BY` from a sorted `Pageable` and fail. One call keeps
  that structural rather than remembered.
- **An unknown Órgano is refused, not answered with an empty page.** The use case checks the
  Órgano exists through `OrganoRepository` and throws the existing `OrganoNotFoundException`,
  which [TASK-0007](TASK-0007-paged-contracts-endpoint.md) renders as the **reused**
  `urn:conxugal:problem-type:organo-not-found`. Without the check, a typed or stale id would
  answer `200` with an empty page — a page of nothing is not the same answer as *there is no such
  Órgano*, and only one of them is true.
- **It applies no default and corrects nothing.** There is no default year, no clamped page and no
  fallback ordering here: every input arrives already validated, and refusing malformed input is
  the driving adapter's job.
- Unit-tested with the repository ports stubbed by Mockito, asserting on the page returned and on
  which ordering was answered rather than on call counts.

## Acceptance criteria

- Each of the four `(SortKey, SortDirection)` pairs answers with the contracts of the requested year
  in that ordering, and the four are distinguishable from one another for the same selection.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #27)
- The page answers the count of the **whole selection**, not of the page: a year of 120 contracts
  read at `size = 50` reports 120 on page 1, page 2 and page 3, and the ordering and the count do
  not change between them. (SPEC-0005 #28)
- Sorting by amount descending puts the largest contract of the **whole year** on the first page,
  not the largest of some subset. (SPEC-0005 #28)
- The repository receives a `Pageable` carrying no `Sort`, whichever ordering was asked for, and
  the use case exposes no way to pass a property name or direction through to a query.
  (SPEC-0005 #28)
- A read naming an Órgano that does not exist raises `OrganoNotFoundException` rather than
  answering an empty page. (No spec criterion; a URL a user can type needs an answer, and
  [TASK-0007](TASK-0007-paged-contracts-endpoint.md) turns it into the reused 404.)
- Unit-tested with no database and no Micronaut context.
