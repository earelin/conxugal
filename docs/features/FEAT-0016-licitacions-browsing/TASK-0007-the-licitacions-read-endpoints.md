---
feat: FEAT-0016
domain: backend
adrs: [0005, 0006, 0010, 0012, 0016, 0020, 0021, 0022]
status: todo
depends_on: [TASK-0005]
---

# The two read endpoints, and this family's entry on the Órgano page

The HTTP surface, authored in
[`docs/api/openapi.yaml`](../../api/openapi.yaml) **first** per
[ADR-0010](../../architecture/0010-design-first-openapi-contract.md), then implemented and verified
against the running instance by
[ADR-0021](../../architecture/0021-openapi-contract-testing-with-schemathesis.md)'s Schemathesis run.

| Method & path | Purpose |
| --- | --- |
| `GET /api/organo/{id}/licitacions` | one page of one year's licitacións in one ordering under at most two narrowings |
| `GET /api/organo/{id}/licitacions/filtros` | the CPV codes and states that year's selection contains |

Both `@Secured(IS_AUTHENTICATED)`, both carrying
[ADR-0012](../../architecture/0012-rate-limit-http-contract.md)'s headers and its shared 429.

**The path segment is `licitacions`, unaccented**, while the domain noun carries an accent
(*licitacións*). It matches the Java package and the family value already published as
`ImportRunOrgano.family`'s `LICITACIONS`, and it keeps the address typeable. The **label** a user
reads is the accented Galician, in `strings.ts`.

## Scope

- **The paged list operation.** Parameters — `page`, `size` and `sort` are
  [ADR-0022](../../architecture/0022-paged-collection-contract-from-micronaut-data.md)'s, declared
  and validated by the operation rather than bound from a `Pageable`:

  | Parameter | Required | Default | Values |
  | --- | --- | --- | --- |
  | `year` | **yes** | — | `YYYY` |
  | `cpv` | no | — | a CPV code as published |
  | `state` | no | — | the source's **state code**, an integer |
  | `sort` | no | `publicationDate,desc` | `publicationDate`/`amount` × `asc`/`desc` |
  | `page` | no | `1` | 1-based |
  | `size` | no | `50` | `1`–`100` |

  ❗ **`state` is the code, never the label.** Codes 101 and 102 are both published *Histórico*; a
  label-keyed filter merges two states the source distinguishes, and no test written against a
  fixture with distinct labels would notice.

  Both operations **refuse an unknown parameter** via the shipped `Refusals.refuseUnknownParameters`.
  A misspelt `cpv` or `sort` is otherwise the quietest wrong answer either could give: a full,
  correct-looking page of an unfiltered selection.
- **The filter-options operation**, taking `year` alone, required.
- **The row schema** — `publicationId`, `publicationDate`, `obxecto`, `state` (`code` **and**
  `label`), `amount` (`value` nullable, `basis` of `AWARDED`/`BUDGET`/`UNSTATED`, `partial`),
  `awardees` (`count`, and `sole` present exactly at 1, carrying `id`, `name` and a **nullable**
  `fiscalId`), and `sourceUrl`.

  **No field states the awarding Órgano** (#28) — every row belongs to the Órgano already open — and
  **no `expediente`, `estimatedValue`, type or `loteCount`**, which are R21's page.
- **`ContratosMenoresPublicationConfiguration` is promoted whole**, renamed for the two families it
  now serves, gaining `urlOf(PublicationId)` beside its existing `urlOf(long)`.

  ❗ It may not reuse `micronaut.http.services.contratosdegalicia.url`: that is the *import client's*
  base URL and `server/docker-compose.yml:56` overrides it to the WireMock stub, so every public link
  would render as `http://contratosdegalicia:8080/...` in dev, preview and e2e. The link a user
  follows and the host the importer scrapes are two facts that coincide in production, and they get
  two properties.

  **One configuration, one template — not one base URL and a template per family**, which two earlier
  drafts of this task proposed in turn. The second rested on the premise that the contratos menores
  configuration composes `"%s/contrato-menor?N=%d"`; **the shipped line is
  `"%s/licitacion?N=%d"`**, there is no `contrato-menor` segment anywhere in the repository, and
  FEAT-0015's measured source contract records that the two families share one address space *and*
  this template. Two families, one string, differing only in `%d` against `%s` — so a per-family
  template would ship two values that must always agree with nothing keeping them in step.
- **The `licitacions` entry on the Órgano member read.** `OrganoFamilies` gains a property and
  `FamiliesResponse` a second `@Nullable` component, carrying the route segment and the
  `LicitacionsSummary` — years, `partial`, `updating`. `OrganoController` gains one injected use case
  and one line. The schema's own text already says *"A family the system gains later adds a property
  here"*, so **nothing published is removed and the change is additive**.
- **Schemas**: `LicitacionsFamily`, `LicitacionsSummary`, `Licitacion`, `LicitacionsPage` as
  `allOf: [PagedCollection, {items}]`, and `LicitacionFilterOptions`. `PagedCollection` itself is
  **reused unchanged** — adding a field to it for one operation is exactly what ADR-0022 exists to
  prevent, which is why the filter options are a read of their own rather than a sixth envelope
  field.

**Out of scope:** the UI, and any change to the contratos menores operation or its schemas.

## Acceptance criteria

`@MicronautTest` integration tests with RestAssured, on
`ContratosMenoresControllerIntegrationTest`'s precedent — with the use cases **mocked**, since the
`application` module's test suite has no datasource.

**So these criteria are about the wire and nothing else**: what the operation accepts, what it
refuses, what it hands the domain, and what shape comes back. *Which rows a selection contains* is
[TASK-0003](TASK-0003-paged-ordered-counted-reads.md)'s and *which codes are offered* is
[TASK-0004](TASK-0004-year-cpv-and-state-facets.md)'s, both proved against PostgreSQL; re-asserting
them through a mock proves only the mock.

- An **unauthenticated** request to either endpoint is denied; an authenticated `USER` and an
  authenticated `ADMIN` both succeed, and neither can modify anything.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #2, #45)
- The response is ADR-0022's envelope — `items`, `page`, `size`, `totalItems`, `totalPages` — mapped
  from the `Page` the use case returns, with the **1-based** `page` the request carried. The mapping
  is the assertion: a `Page` at 0-based index 2 comes back as `page: 3`. *(Exhaustive paging over a
  real selection is [TASK-0003](TASK-0003-paged-ordered-counted-reads.md)'s.)* (SPEC-0008 #28)
- ❗ **The defaults reach the domain.** A request stating no `sort`, `page` or `size` invokes the use
  case with `PUBLICATION_DATE`, `DESC`, page 1 and size 50 — asserted on the arguments the mock
  received, since a default applied nowhere and a default applied twice look identical in the response.
  (SPEC-0008 #30)
- **Each of the four `sort` spellings reaches the domain as its own (key, direction) pair**, so no two
  orderings collapse into one on the wire. (SPEC-0008 #34)
- **`year`, `cpv` and `state` reach the domain as given** — the year parsed, the state as an integer
  code, the CPV unaltered — and an **absent** `cpv` or `state` reaches it as absent rather than as an
  empty string. (SPEC-0008 #33)
- A request with **no `year`**, or a malformed one, is **400** — not an all-years list and not a
  default applied server-side. (SPEC-0008 #32)
- A `sort` naming another property, or a direction that is not `asc`/`desc` — including the longer
  spelling the framework would silently accept as ascending — is **400**. A `page` below 1 or a
  `size` outside 1–100 is **400**. An **unknown parameter** is 400. Parameterised over the table, on
  the shipped `selection_is_refused` precedent. (*No criterion — SPEC-0008 has no analogue of
  SPEC-0005 #28. See the README's candidate-criteria table.*)
- A `cpv` or `state` naming something the year does not contain is **not** an error: an empty page
  with `totalItems` of **0**. Reachable from a stale shared link. (SPEC-0008 #33)
- ❗ **`filtros` publishes the state's `code`, and the code is what the list operation's `state`
  parameter takes.** A `LicitacionFilterOptions` holding codes **101 and 102**, both labelled
  *Histórico*, serialises as **two** entries with distinct `code`s; and `?state=102` reaches the domain
  as the code `102`, not as a label and not as a list index. This is the criterion the task's bolded
  warning is about: without it a label-keyed implementation passes everything else here.
  (SPEC-0008 #33)
- **`filtros` accepts `year` and refuses everything else** — a `cpv`, a `state`, a `sort` or a `page`
  on that operation is **400**, since it takes none of them. (SPEC-0008 #33)
- A state whose label the domain answered as **null** serialises with a null label and its code, and a
  CPV with a null description likewise — neither is dropped and neither becomes an empty string.
  (SPEC-0008 #33)
- An unknown Órgano id is **404** carrying the **existing**
  `urn:conxugal:problem-type:organo-not-found`, from both endpoints.
- **The row DTO is a faithful projection of `VisibleLicitacion`.** Given a domain row with one
  awardee, `awardees.count` is 1 and `sole` carries `id`, `name` and `fiscalId`; given one with three,
  the count is 3 and `sole` is **absent**; given one with none, the count is **0**. Given a `sole`
  whose operador holds no identifier, `fiscalId` is **null and the awardee is still present**. In no
  case does any response carry a per-row awardee name. (SPEC-0008 #20, #24, #29)
- `amount` serialises the `StatedAmount` it was given — `basis`, `partial`, and a `value` that is
  **absent only** when the basis is `UNSTATED`. The controller computes none of them. (SPEC-0008 #35)
- **No response carries an `organo` key on a row** (#28), and none carries `expediente`,
  `estimatedValue`, a type or `loteCount` — asserted against the serialised body, so a later field
  cannot be added without meeting R21's boundary. (SPEC-0008 #28)
- Both operations carry **[ADR-0012](../../architecture/0012-rate-limit-http-contract.md)'s three
  `RateLimit-*` headers** and its shared 429 shape.
- `sourceUrl` is absolute and addresses the publication at the official source. Overriding the
  **promoted configuration's base URL** moves **both** families' links; overriding the import client's
  URL moves **neither**. Both directions asserted, since the second is the trap. The contratos menores
  link is **byte-for-byte what it was** before the promotion. (SPEC-0008 #28)
- `GET /api/organo/{id}` carries a `licitacions` entry for an Órgano with visible licitacións, and
  **omits it entirely** for one without — with the `contratosMenores` entry unaffected in both
  directions, and an Órgano holding only licitacións returning a families map with one entry.
  (SPEC-0008 #26, #27, #37)
- Schemathesis passes against the two new operations and the amended member read, and
  `scripts/openapi-lint.sh` passes.
