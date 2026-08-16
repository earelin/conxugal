---
feat: FEAT-0011
domain: backend
adrs: [0002, 0022]
status: done
depends_on: [TASK-0001, TASK-0003]
---

# `ListContratosMenores`

The domain read answering one Órgano's contracts of one year, in one ordering, one page at a
time, with the count of the whole selection. It is where a validated sort key and direction
become **one of four fixed statements**, and it is the last place a `Sort` could reach a query —
so it is the place that guarantees none does.

## Scope

- `ListContratosMenores` in `gal.conxugal.domain.contrato`, taking an `OrganoId`, a
  `YearSelection`, a `SortKey`, a `Sort.Order.Direction` and a `Pageable`, and answering
  `Page<VisibleContratoMenor>`.
- **The mapping is a total function over a closed set**: the four `(SortKey, Sort.Order.Direction)` pairs
  become the four `Sort` values the single repository read
  [TASK-0003](TASK-0003-paged-ordered-counted-reads.md) implements is called with. It takes no
  default branch and no fallback — every pair is a case, because both types are enums and the
  compiler can see the set is covered.
- **This use case is the only place a `Sort` is built, and it builds one only from those enums.**
  A property name never arrives here as text: the driving adapter has already refused anything
  outside the two spellings, and what reaches this method is a `SortKey` and a `Sort.Order.Direction`.
  That is what keeps the emitted `ORDER BY` free of caller-supplied text on a native statement —
  the invariant [ADR-0022](../../architecture/0022-paged-collection-contract-from-micronaut-data.md)
  protects, held here as *what the sort is built from* rather than as the sort being absent. **No
  other caller may build one**, and nothing binds a `Pageable` from the request.
- **Every `Sort` it builds ends with `sourceId` in the direction of the key it breaks ties for.**
  Neither sort key is unique, so this is what makes the order total and paging denote; and only the
  matching direction is a plain backward scan of the index. It is appended in one place here rather
  than remembered at four call sites, and a unit test pins all four.
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
  the `Sort` the repository was handed rather than on call counts.

## What building it found

> **Amended in one place, and it is not a change of design.** Every acceptance criterion below is
> met as written, and the invariant the scope names — that the emitted `ORDER BY` is built from two
> enums and never from a caller's text — holds exactly as described. What changed is *where the
> four cases are written*.
>
> - **The total mapping already exists: it is `SortKey.ordering(Sort.Order.Direction)`.**
>   [TASK-0003](TASK-0003-paged-ordered-counted-reads.md) moved it into the enum, because three
>   places need the same four orderings — the paged read, the schema test that pins their plans,
>   and the adapter, which derives the columns it will accept from `SortKey.values()` × the two
>   directions rather than restating them. It is already total over both enums with no default
>   branch, and it is already what appends `source_id` in the direction of the key it breaks ties
>   for. So this use case *attaches* that ordering rather than assembling a second one: a switch
>   written out here would duplicate a tested single place, and the adapter's allow-list would then
>   be derived from one of the two copies.
>
>   The scope's sentence "this use case is the only place a `Sort` is built" therefore reads, after
>   TASK-0003, as *the only place a `Sort` is decided*: nothing else chooses an ordering, nothing
>   else calls `ordering(...)` on the browse path, and nothing binds a `Pageable` from a request.
>   The four `(SortKey, Sort.Order.Direction)` pairs are still pinned by a unit test here — against
>   the pageable the store is handed, with the clauses written out rather than rendered from
>   `SortKey`, so an expectation cannot move along with the expression it is checking.
> - **The incoming ordering is replaced, not appended to.** `Pageable.order(...)` adds to whatever
>   sort the pageable already carried, so the use case rebuilds the pageable —
>   `Pageable.from(number, size, key.ordering(direction))` — and a test hands it a pageable
>   carrying a property no `SortKey` could have produced to pin that it does not survive. The page
>   number and the size pass through untouched, as the scope requires.

## Acceptance criteria

- Each of the four `(SortKey, Sort.Order.Direction)` pairs answers with the contracts of the requested year
  in that ordering, and the four are distinguishable from one another for the same selection.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #27)
- The page answers the count of the **whole selection**, not of the page: a year of 120 contracts
  read at `size = 50` reports 120 on page 1, page 2 and page 3, and the ordering and the count do
  not change between them. (SPEC-0005 #28)
- Sorting by amount descending puts the largest contract of the **whole year** on the first page,
  not the largest of some subset. (SPEC-0005 #28)
- The repository receives a `Pageable` whose `Sort` names only properties a `SortKey` selected and
  ends with `sourceId` in the direction of its key, for each of the four pairs; the use case
  exposes no way to pass a property name or a direction through as text. (SPEC-0005 #28)
- A read naming an Órgano that does not exist raises `OrganoNotFoundException` rather than
  answering an empty page. (No spec criterion; a URL a user can type needs an answer, and
  [TASK-0007](TASK-0007-paged-contracts-endpoint.md) turns it into the reused 404.)
- Unit-tested with no database and no Micronaut context.
