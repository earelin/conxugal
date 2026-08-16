---
feat: FEAT-0011
domain: backend
adrs: [0002, 0005, 0006, 0010, 0012, 0016, 0020, 0021, 0022]
status: done
depends_on: [TASK-0005]
---

# `GET /api/organo/{id}/contratos-menores`

The one endpoint this feature adds: a page of one Órgano's contratos menores of one year, in one
ordering, with both totals. Authored contract-first in `docs/api/openapi.yaml`
([ADR-0010](../../architecture/0010-design-first-openapi-contract.md)) and then implemented, named
per [ADR-0020](../../architecture/0020-actions-as-verbs-in-rest-paths.md) and the
[ADR-0016](../../architecture/0016-rest-resource-naming.md) it supersedes — a member at the
singular `/api/organo/{id}`, so nothing collides with a sub-resource of the plural set.

**Paging is [ADR-0022](../../architecture/0022-paged-collection-contract-from-micronaut-data.md)'s,
not this task's**: the 1-based `page`, the envelope, the refusal of out-of-range values and the
mapping from Micronaut Data's `Page` are recorded there and must not be restated or varied here.

## Scope

- **The contract, first.** The operation under `/api/organo/{id}/contratos-menores`, reusing the
  existing `OrganoId` path parameter, carrying
  [ADR-0012](../../architecture/0012-rate-limit-http-contract.md)'s three `RateLimit-*` headers and
  the shared `429`, `401`, `400`, `404` and `500` responses the sibling operations already use.

  | Parameter | Required | Default | Values |
  | --- | --- | --- | --- |
  | `year` | **yes** | — | `YYYY` |
  | `sort` | no | `publicationDate,desc` | `publicationDate` or `amount`, `,asc` or `,desc` |
  | `page` | no | `1` | 1-based; `< 1` is a 400 |
  | `size` | no | `50` | `1`–`100`; outside that is a 400 |

  Path segments stay Galician nouns; fields and parameters stay English, with `obxecto` the one
  field that is Galician because it already is in the domain and the store.
- **The envelope as a reusable schema.** ADR-0022's
  `{ items, page, size, totalItems, totalPages }` declared once in `openapi.yaml` and once in the
  `application` module, because SPEC-0006's and SPEC-0007's lists will reference the same one. The
  row schema carries `sourceId`, `publicationDate`, `obxecto`, `amount`, `duration`, `awardee`
  (`name` and `fiscalId`) and `sourceUrl`. `publicationDate`, `amount` and `awardee` are
  **required** — R28 withholds a contract without them, so the wire shape has no optionality for a
  client to branch on — and there is **no operador id** and **no awarding Órgano** on the row.
- **Both conversions live in the controller, and nowhere else.** Inbound: the validated
  parameters become a `YearSelection`, a `SortKey`, a `Sort.Order.Direction` and a **0-based, unsorted**
  `Pageable` — unsorted because the ordering travels as the two enums and
  [TASK-0005](TASK-0005-list-contratos-menores-use-case.md) is the one place a `Sort` is built from
  them, tiebreaker included. Outbound: the `Page` becomes the 1-based envelope, with `totalPages` taken from
  `getTotalPages()` rather than divided again. Nothing above the controller sees a `Pageable` and
  nothing below it sees the envelope.
- **`sort` is refused, not degraded.** A property other than `publicationDate` or `amount`, or a
  direction other than `asc`/`desc`, is a **400** — parsed through
  [TASK-0001](TASK-0001-selection-value-types-and-read-ports.md)'s `Optional`-answering `parse`,
  never bound as a `Sort`. This is the feature's **security invariant**: the framework's binder
  accepts any property name and silently degrades an unknown direction to ascending, and for a
  native `@Query` the property is interpolated into `ORDER BY` verbatim and unescaped. No
  `Pageable` is bound from the request, so the mechanism is absent rather than guarded.
- **A missing or malformed `year` is a 400**, not an all-years list and not a server-side default.
  The default year is the client's, taken from the summary
  [TASK-0006](TASK-0006-section-summary-port-and-schema.md) publishes.
- **An unknown Órgano reuses `urn:conxugal:problem-type:organo-not-found`** through the existing
  `OrganoNotFoundExceptionHandler`. If its package-private visibility keeps it from applying
  outside `rest/admin/organos`, it moves to a shared error package — a **second** problem type for
  the same condition is not an option.
- **`sourceUrl` is composed on the server**, from a `@ConfigurationProperties` record of this
  feature's own — `conxugal.contratos-menores.publication` with a base URL defaulting to
  `https://www.contratosdegalicia.gal` — appended with `licitacion?N={sourceId}`, the address
  FEAT-0009 established is derivable from the identifier the row already carries.
  - **It must not reuse `micronaut.http.services.contratosdegalicia.url`.** That is the *import
    client's* base URL, and `server/docker-compose.yml` overrides it to the WireMock stub, so
    every public link would render as `http://contratosdegalicia:8080/…` in dev, preview and e2e.
    The host a user follows and the host the importer scrapes are two facts that coincide only in
    production.
- `@Secured(SecurityRule.IS_AUTHENTICATED)` — R2 grants the read to `USER` and `ADMIN` alike and
  denies an unauthenticated visitor, which is the mitigation R26 rests on
  ([ADR-0005](../../architecture/0005-session-based-authentication.md)). It grants no ability to
  modify anything, and it lives under the reserved `/api/` prefix
  ([ADR-0006](../../architecture/0006-reserved-api-url-prefix.md)).
- `server/application/build.gradle.kts` **declares `micronaut-data-model`** rather than resolving
  `Pageable` and `Page` through the domain's `api(...)`. `micronaut-data-runtime` stays out: no
  `Pageable` is bound from a request, so its argument binder is not needed.
- Integration tests in `server/application/src/integrationTest`, plus the Schemathesis run
  ([ADR-0021](../../architecture/0021-openapi-contract-testing-with-schemathesis.md)) through
  `scripts/contract-test.sh`.

## What building it found

> **The `OrganoNotFoundExceptionHandler` contingency did not fire, and it is now answered rather
> than assumed.** Micronaut resolves an `ExceptionHandler` by the exception type it is declared
> against, not by the package the controller sits in, and a package-private `@Singleton` registers
> like any other — so the handler under `rest/admin/organos` already covers a read outside it. The
> scope's *move it to a shared error package* branch is therefore unused and no second problem type
> exists. The package name is now a little misleading, which is a rename for whoever adds the third
> caller rather than a change worth making on this one.
>
> **Two absences the serializer would have dropped, and only one of them was foreseen.** The row's
> null `obxecto` and `duration` needed `@JsonInclude(ALWAYS)`, as every response carrying a
> meaningful null does. So did the envelope's **empty `items`** — the default inclusion is
> `NON_EMPTY`, so a page beyond the last went out as `{"page":100,…}` with the key the contract
> marks required missing altogether, on exactly the response a client has to read to clamp. Both
> are asserted on the serialised keys rather than on the record's fields, which is what keeps the
> annotation load-bearing.
>
> **An unknown query parameter was accepted, and Schemathesis is what noticed.** This is the first
> operation in the contract with query parameters at all, so the coverage phase's *unexpected
> property* case had never applied before; it sent one and got a `200`. Ignoring it is the quietest
> wrong answer this operation could give — `?srot=amount,asc` would have been answered in the
> default ordering, and the envelope states no ordering back — so an unrecognised parameter is now
> a **400**, which is the same rule as refusing an ordering that was never offered, one level up.
>
> **`amount` is declared unbounded, and that is a decision rather than an omission.** A first draft
> gave it `minimum: 0` and a ceiling, which nothing in the system enforces: `Money` only null-checks,
> the column is a bare `NUMERIC`, and the import stores what the source published. A published
> correction or a mis-scaled figure would then have made the server answer `200` with a body its own
> contract rejects — and no test could have caught it, the contract run's seeded Órgano holding no
> contracts. The bounds are gone, on the same footing as `obxecto` carrying no length cap: the row
> mirrors what was published, and a claim only the document makes is worse than no claim. Bounding
> it for real is a `CHECK` and a domain guard, which belongs to whoever owns the store.
>
> **A refusal has to be thrown as a problem, not as a status.** `HttpStatusException` reaches
> Micronaut's own handler and is rendered `{"type":"about:blank","title":"Bad Request","status":400}`
> — the message dropped. On an operation with five distinct refusal rules that leaves a caller to
> guess which one it broke, so the refusals build a `Problem` with a `detail` naming the parameter,
> which the shared `BadRequest` response already admits without a contract change.
>
> **`ApiUrlPrefixArchTest`'s sibling rule had to be narrowed, and *how* it is narrowed turned out to
> matter more than that it is.** `ModuleBoundariesArchTest.APPLICATION_DOES_NOT_DEPEND_ON_PERSISTENCE`
> predates [ADR-0022](../../architecture/0022-paged-collection-contract-from-micronaut-data.md) and
> forbade `application` naming anything under `io.micronaut.data..` — which is precisely the
> widening that record states and accepts.
>
> The first narrowing excepted `Page`, `Pageable` and `Sort` through ArchUnit's `belongToAnyOf`,
> which matches **nested types too** — that is what makes `Sort.Order.Direction` resolve, and it is
> also what would have let `Sort.Order` in. `Sort.of(Sort.Order.asc(text))` in a driving adapter
> would then have compiled and passed the rule, which is exactly the ordering-built-from-raw-input
> this feature's security invariant exists to foreclose; the only remaining guard would have been
> one adapter's private allow-list. The rule now excepts **by identity** — `Page`, `Pageable` and
> `Sort.Order.Direction`, those three classes and no nested type of any of them — so the sort itself
> is unbuildable in `application` and a later *dynamic* ordering has to move that line before it can
> compile. A negative check confirms the exception is load-bearing rather than vacuous: swapping
> `Sort.Order.Direction` for `Sort` fails the rule.

## Acceptance criteria

- An authenticated caller reads a page of one Órgano's contracts of one year and receives
  `{ items, page, size, totalItems, totalPages }` with a 1-based `page` equal to the one requested.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #23, #28)
- An unauthenticated caller receives `401` and no data. (SPEC-0005 #2, #39 authentication half)
- A request with **no `year`** is `400`; so is a malformed one. No all-years list exists to fall
  back to and no default is applied. (SPEC-0005 #27, no-all-years half)
- `?sort=obxecto`, `?sort=amount,descending` and `?sort=` are each `400`; the four valid
  combinations each answer in their own ordering, and `sort` omitted answers newest-published
  first. (SPEC-0005 #28)
- `page=0`, `size=0` and `size=101` are each `400` — refused, not clamped. A `page` beyond the last
  answers `200` with an empty `items` and the selection's true `totalItems` and `totalPages`.
  (SPEC-0005 #23)
- Every row carries the source identifier, the publication date, the `obxecto`, the amount, the
  duration, the awardee's name and canonical fiscal identifier, and an absolute `sourceUrl` to the
  publication at the official source. No row carries a null date, amount or awardee, and no field
  states the awarding Órgano. (SPEC-0005 #16, #21, #25 source half, #39)
- `sourceUrl` is built from this feature's own property and is unaffected by
  `micronaut.http.services.contratosdegalicia.url` being pointed at a stub — proven by an
  integration test that overrides the import client's URL and asserts the link is unchanged.
  (SPEC-0005 #25, source half)
- An unknown Órgano id answers `404` with `urn:conxugal:problem-type:organo-not-found`, the same
  type the existing operations use. An Órgano that exists but holds no visible contracts of this
  family is **not** an error here — it simply has no section, which
  [TASK-0006](TASK-0006-section-summary-port-and-schema.md) reports and FEAT-0013 renders.
  (SPEC-0005 #50)
- Every response carries the three `RateLimit-*` headers, and the operation declares the shared
  `429`.
- `scripts/openapi-lint.sh` passes, and the Schemathesis run passes against the running instance.
