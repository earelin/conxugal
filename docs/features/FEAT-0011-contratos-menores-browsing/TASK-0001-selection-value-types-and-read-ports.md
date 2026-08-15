---
feat: FEAT-0011
domain: backend
adrs: [0002, 0019, 0022]
status: done
depends_on: []
---

# Selection value types and the browse read port

The domain vocabulary a contratos menores read is asked in — a year, a sort key, a direction —
the shape a paged read answers with, and the port method that serves it.
Pure `domain` ([ADR-0002](../../architecture/0002-hexagonal-architecture.md)): no SQL, no
HTTP, and **no `Sort` built here** — the port receives one on its `Pageable`, and
[TASK-0005](TASK-0005-list-contratos-menores-use-case.md) is what constructs it. Nothing here is
wired to a caller; the use case is
[TASK-0005](TASK-0005-list-contratos-menores-use-case.md) and the endpoint is
[TASK-0007](TASK-0007-paged-contracts-endpoint.md).

## Scope

- **`YearSelection`** in `gal.conxugal.domain.contrato` — a record over one year, with a static
  `of(int)` and a `parse(String)` answering `Optional<YearSelection>` for exactly a four-digit
  `YYYY`. **There is no second case and no absence**: no `allYears()`, no `undated()`, no
  nullable field and no sentinel value. That is how R19's *there is no all-years list* is held
  as a type rather than as a validation every caller has to remember, and it is why the type
  exists at all rather than an `int` being passed around.
  - `parse` answers `Optional` rather than throwing: the refusal is a **400** and only the
    driving adapter knows that, so the domain reports *no value* and
    [TASK-0007](TASK-0007-paged-contracts-endpoint.md) renders it.
  - It carries `@TypeDef(type = DataType.INTEGER, converter = YearSelectionConverter.class)` and
    a converter beside it, the pattern `Money` and `FiscalIdentifier` already use
    ([ADR-0019](../../architecture/0019-typed-aggregate-identifiers.md)), so it can be a query
    parameter on the ports below without unwrapping at the boundary. The converter carries **both**
    halves: the attribute half converts the selection a read is scoped by, and the `TypeConverter`
    half is what rebuilds each year of
    [TASK-0004](TASK-0004-year-facets-and-section-state.md)'s facet read, which answers a column of
    years with no aggregate behind it.
  - **Building one and parsing one admit the same set**: the constructor refuses a year outside
    `1000`–`9999`, so `of(0)` is not a selection either. A type claiming a year cannot be asked for
    anything else would be untrue if its two entry points disagreed about what a year is.
- **`SortKey`** in `gal.conxugal.domain.contrato` — an enum of `PUBLICATION_DATE` and `AMOUNT`,
  with a `parse(String)` answering `Optional` and accepting exactly the spellings the contract
  publishes: `publicationDate` and `amount`. Anything else — another property, another case —
  answers empty. R19's two sorts are a **closed set**, and a parse that quietly widened it is the
  defect the feature's security invariant is about.
  - **No direction type of this feature's own.** The ordering reaches the store as a
    `io.micronaut.data.model.Sort`, whose `Sort.Order.Direction` is already the closed `ASC`/`DESC`
    enum a direction needs to be; a second one beside it would exist only to be mapped onto that
    one. The `asc`/`desc` spellings are parsed where they are a contract — the driving adapter, in
    [TASK-0007](TASK-0007-paged-contracts-endpoint.md) — rather than in the domain.
  - `SortKey` stays here because its values name **contratos menores properties**; that is what
    makes it this family's vocabulary rather than pagination's.
- **`VisibleContratoMenor`** — the projection a browse read answers with, carrying exactly what
  R16 puts on a row: `sourceId`, `publicationDate`, `obxecto`, `amount` (`Money`), `duration`,
  `awardeeName` and `awardeeFiscalId` (`FiscalIdentifier`). `publicationDate`, `amount`,
  `awardeeName` and `awardeeFiscalId` are **non-null**, which is R28's withholding expressed as a
  type: a contract missing any of them is not a visible contract, so no reader downstream needs a
  branch for it.
  - **It is a projection, not the `ContratoMenor` aggregate**, and that is a decision this task
    takes rather than an omission. The aggregate's awardee is an `OperadorEconomico` carrying an
    embedded `NomeRank` and a `ONE_TO_MANY` set of alternative names; materialising one from the
    hand-written statements [TASK-0003](TASK-0003-paged-ordered-counted-reads.md) needs would
    mean aliasing an embedded value and assembling a collection per row, all to reach two fields
    a row shows. The projection is the smaller thing that answers the question.
  - It is `@Introspected` so a result row can be read onto it. **Introspection settles the shape
    and not the conversion**, and the difference bites exactly once here: a projection's component
    inherits a converted type's mapping only where the aggregate it projects from carries a
    property of the **same name and type**. `amount` does — `ContratoMenor` holds a `Money` — while
    `awardeeFiscalId` does not, because the contract reaches its awardee through a relation rather
    than holding the value, so it arrives as bare text. `FiscalIdentifierConverter` therefore gains
    the `TypeConverter` half `OrganoIdConverter` already carries, and this task adds it rather than
    leaving [TASK-0003](TASK-0003-paged-ordered-counted-reads.md) to meet it as a failing
    integration test.
- **One method on a new `VisibleContratoMenorRepository` port** — `page(OrganoId, YearSelection,
  Pageable)` answering `Page<VisibleContratoMenor>`. Declared here, implemented in
  [TASK-0003](TASK-0003-paged-ordered-counted-reads.md).
  - **The ordering arrives on the `Pageable`'s `Sort`**, so one statement serves all four orderings
    rather than four serving one each. The framework appends a native statement's ordering at the
    end of the SQL, which is what makes one statement enough — and what obliges the statement to
    carry **no `ORDER BY` of its own**, since one that already ordered would emit two.
  - The port's javadoc states the three obligations that travel with that `Pageable`, all the
    caller's because the statement cannot enforce them:
    - **the `Sort` is built from `SortKey` and `Sort.Order.Direction`, never from a caller's string.** The
      two enums are the closed set of orderings offered; the statement is native, so a property
      name is appended verbatim and unescaped, and a sort assembled from raw input would put a
      caller's text into the emitted SQL. This is where the feature's security invariant now
      lives — in *what the sort is built from* rather than in the sort being absent;
    - **it ends with the `source_id` tiebreaker in the direction of the key it breaks**, so the
      order is total and paging denotes;
    - **the statement adds no ordering of its own.**
  - **A port of its own rather than four more methods on `ContratoMenorRepository`**, and the
    reason is that the alternative does not compile. `JdbcContratoMenorRepository` is an abstract
    `@JdbcRepository` implementing that port, and Micronaut Data's annotation processor must
    implement **every** abstract method it inherits — so four methods declared with their
    statements deferred to [TASK-0003](TASK-0003-paged-ordered-counted-reads.md), which itself
    waits on [TASK-0002](TASK-0002-visible-browse-schema-and-indexes.md)'s `publication_year`,
    would fail annotation processing and leave this task's build red. A port nothing implements
    yet has no such problem, and it is the shape `OrganosWithVisibleContracts` already takes: that
    same adapter implements three interfaces, one of them a single-purpose read port. Reading a
    browse page and writing an import batch are two questions of one table, and only the second
    of them may ever write.
- **What is deliberately not here**: no constructed `Sort`, no `@QueryValue`, no problem type, no
  default page size. Those are HTTP vocabulary and belong to
  [TASK-0007](TASK-0007-paged-contracts-endpoint.md); building the four `Sort` values from a
  validated key and direction is [TASK-0005](TASK-0005-list-contratos-menores-use-case.md)'s.
  `Pageable`/`Page` on a domain port is the leak
  [ADR-0022](../../architecture/0022-paged-collection-contract-from-micronaut-data.md) accepted,
  and `server/domain/build.gradle.kts` already declares `api(libs.micronaut.data.model)`.

## Acceptance criteria

- A `YearSelection` cannot be built without a year: the type exposes no factory, constant or
  constructor yielding an absent, *all years* or *undated* value, and a unit test asserts that a
  selection always answers with a year.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #27, no-all-years half)
- `YearSelection.parse` accepts a four-digit year and answers empty for an absent, blank,
  non-numeric or otherwise malformed value — including `all` and `undated`. (SPEC-0005 #27)
- Every year `YearSelection.parse` accepts can be built directly, and every value the constructor
  refuses cannot be spelled for `parse` — the two admit one set. (SPEC-0005 #27)
- `SortKey.parse` accepts exactly `publicationDate` and `amount`. Every other input — a different
  property name, a different case, an empty string — answers empty rather than a default. The
  matching refusal for `asc`/`desc` is [TASK-0007](TASK-0007-paged-contracts-endpoint.md)'s, since
  no direction type is declared here. (SPEC-0005 #28)
- `VisibleContratoMenor` refuses construction with a null publication date, amount, awardee name
  or awardee fiscal identifier, and permits a null `obxecto` and `duration`. (SPEC-0005 #11, #50)
- `VisibleContratoMenorRepository` declares one read, `page(OrganoId, YearSelection, Pageable)`,
  returning `Page<VisibleContratoMenor>` and taking no property name or direction of its own — the
  ordering reaches it only as the `Sort` its caller built from `SortKey` and
  `Sort.Order.Direction`. **The compiler is the check**: nothing can compile against a method that
  does not exist or a signature that differs, so this criterion carries no test of its own. The
  invariants that a test *can* fail on — what the `Sort` is built from, and that it ends with the
  tiebreaker — are [TASK-0005](TASK-0005-list-contratos-menores-use-case.md)'s, because that is
  where a `Sort` is built. (SPEC-0005 #28)
- The `domain` module compiles with no reference to `io.micronaut.http` or any other HTTP type in
  these classes.
- Unit-tested with JUnit and AssertJ; no database and no Micronaut context.
