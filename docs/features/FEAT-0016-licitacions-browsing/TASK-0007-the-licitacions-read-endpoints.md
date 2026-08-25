---
feat: FEAT-0016
domain: backend
adrs: [0005, 0006, 0010, 0012, 0016, 0020, 0021, 0022]
status: todo
depends_on: [TASK-0005, TASK-0006]
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
- **This family's publication URL property.** ❗ It must **not** reuse
  `micronaut.http.services.contratosdegalicia.url`: that is the *import client's* base URL and
  `server/docker-compose.yml` overrides it to the WireMock stub, so every public link would render as
  `http://contratosdegalicia:8080/...` in dev, preview and e2e. FEAT-0011 met this and solved it with
  a separate property; this family gets its own, on the same reasoning — the link a user follows and
  the host the importer scrapes are two facts that happen to coincide in production.
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
`ContratosMenoresControllerIntegrationTest`'s precedent:

- An **unauthenticated** request to either endpoint is denied; an authenticated `USER` and an
  authenticated `ADMIN` both succeed, and neither can modify anything.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #2, #45)
- The response is ADR-0022's envelope — `items`, `page`, `size`, `totalItems`, `totalPages` — with
  the **1-based** `page` the request carried, and paging first/previous/next/last over a seeded year
  yields exactly `totalItems` rows with none repeated and none skipped. (SPEC-0008 #28)
- A request with **no `year`**, or a malformed one, is **400** — not an all-years list and not a
  default applied server-side. (SPEC-0008 #32)
- A `sort` naming another property, or a direction that is not `asc`/`desc` — including the longer
  spelling the framework would silently accept as ascending — is **400**. A `page` below 1 or a
  `size` outside 1–100 is **400**. An **unknown parameter** is 400. Parameterised over the table, on
  the shipped `selection_is_refused` precedent. (SPEC-0008 #34)
- A `cpv` or `state` naming something the year does not contain is **not** an error: an empty page
  with `totalItems` of **0**. Reachable from a stale shared link. (SPEC-0008 #33)
- An unknown Órgano id is **404** carrying the **existing**
  `urn:conxugal:problem-type:organo-not-found`, from both endpoints.
- A row whose procedure has exactly one awardee carries `awardees.count` of 1 and a `sole` with its
  `id`, `name` and `fiscalId`; one whose lotes went to several carries the count and **no** `sole`;
  one whose award resolved to nobody carries a count of **0**. In no case does any response carry a
  per-row awardee name. (SPEC-0008 #20, #24, #29)
- A row's `amount.basis` is `BUDGET` for an unawarded procedure, `AWARDED` for an awarded one,
  `partial` true for a partly awarded one, and `UNSTATED` with a null `value` for one with neither —
  and `value` is **never** absent unless the basis is `UNSTATED`. (SPEC-0008 #35)
- `sourceUrl` is absolute, addresses the publication at the official source, and is composed from
  **this family's own property** — asserted by overriding that property alone and observing the link
  change while the import client's base URL is untouched. (SPEC-0008 #28)
- `GET /api/organo/{id}` carries a `licitacions` entry for an Órgano with visible licitacións, and
  **omits it entirely** for one without — with the `contratosMenores` entry unaffected in both
  directions, and an Órgano holding only licitacións returning a families map with one entry.
  (SPEC-0008 #26, #27, #37)
- Schemathesis passes against the two new operations and the amended member read, and
  `scripts/openapi-lint.sh` passes.
