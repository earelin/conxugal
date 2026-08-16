---
feat: FEAT-0011
domain: backend
adrs: [0002, 0005, 0006, 0010, 0012, 0016, 0020, 0021, 0022]
status: todo
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
  `OrganoNotFoundExceptionHandler`. **Nothing to do here: the move landed with
  [FEAT-0013](../FEAT-0013-organo-contracts-page/TASK-0001-organo-member-read.md)'s member read**,
  which reached this condition first — the handler now sits in the shared `http/error` package and
  applies to this path as it stands. A **second** problem type for the same condition is not an
  option.
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
