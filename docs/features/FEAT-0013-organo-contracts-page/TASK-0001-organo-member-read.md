---
feat: FEAT-0013
domain: backend
adrs: [0002, 0005, 0006, 0010, 0012, 0016, 0020, 0021]
status: done
depends_on: []
---

# `GET /api/organo/{id}`: the Órgano, and one summary per family it holds

The one read this feature adds, and the only request the page makes: an Órgano's own attributes
and a **`families` map carrying one summary per contract family it holds visible data for**.
Authored contract-first in `docs/api/openapi.yaml`
([ADR-0010](../../architecture/0010-design-first-openapi-contract.md)) and then implemented, named
per [ADR-0020](../../architecture/0020-actions-as-verbs-in-rest-paths.md) and the
[ADR-0016](../../architecture/0016-rest-resource-naming.md) it supersedes — a member at the
singular `/api/organo/{id}`, the same member path
[FEAT-0011](../FEAT-0011-contratos-menores-browsing/README.md)'s contract list hangs off.

**It replaces three round trips with one.** The page's name, its tab bar and its opening section's
year chooser all come from this response, which is why an earlier draft's
`…/contratos/familias` and `…/contratos-menores/resumo` endpoints do not exist.

**Depends on [FEAT-0011](../FEAT-0011-contratos-menores-browsing/README.md)'s
[TASK-0006](../FEAT-0011-contratos-menores-browsing/TASK-0006-section-summary-port-and-schema.md)**
— the `DescribeContratosMenoresSection` port this controller injects and the
`ContratosMenoresSummary` schema this operation `$ref`s. That dependency is outside this feature
and so is not in `depends_on:`, which names only tasks here.

## Scope

- **The contract, first.** A `GET` operation under `/api/organo/{id}`, reusing the existing
  `OrganoId` path parameter, carrying
  [ADR-0012](../../architecture/0012-rate-limit-http-contract.md)'s three `RateLimit-*` headers and
  the shared `429`, `401`, `404` and `500` responses the sibling operations already use.

  ```json
  {
    "id": "…",
    "name": "Servizo Galego de Saúde",
    "families": {
      "contratosMenores": {
        "route": "contratos-menores",
        "summary": { "years": [2025, 2024, 2023], "partial": false, "updating": true }
      }
    }
  }
  ```

  `id`, `name` and `families` are all **required**; `families` may be empty, and no property of it
  is required.
- **`families` is an object keyed by family**, and each entry carries the `route` its section is
  mounted at, so no lookup table exists that could disagree with the router: the key identifies,
  the route addresses, and the client reads the second rather than inferring it from the first.
  The schema declares `contratosMenores` as its one optional property, `$ref`-ing a
  `ContratosMenoresFamily` that pairs `route` with **FEAT-0011's** `ContratosMenoresSummary` under
  `summary` — `$ref`-ed, never restated — and sets `additionalProperties: false`. The summary is
  nested rather than spread beside the route because its shape is FEAT-0011's: flattening them
  would put a field of this page's into a schema another feature owns. A new family adds a property
  and a `$ref`; it changes nothing already declared.
- **Presence is the summary's existence.** The entry appears when
  `DescribeContratosMenoresSection` answers a section and is **absent** when it answers
  `Optional.empty()`. No boolean says whether a family has data, because there is nothing for such
  a boolean to disagree with.
- **The empty map must reach the wire as `{}`**, not be dropped: the page renders *this Órgano
  holds no contracts* from it, and an absent key is a different fact from an empty one. The
  existing `OrganoResponse` had to override the serializer's `NON_EMPTY` inclusion for the same
  reason, and the same hazard applies here — with the opposite requirement for the map's own
  properties, where an absent family must **not** serialise as an explicit null.
- **The composition lives in the controller and nowhere else** — a new `OrganoController` beside
  the plural `OrganosController` in `gal.conxugal.application.rest.organos`, injecting the Órgano
  read and each family's summary port and assembling the response. The response record is
  `OrganoMemberResponse`, since `OrganoResponse` is already the catalogue row and both are
  serialised from this package. Nothing generic is introduced for *a family*: there is one port
  today, injected by its own type, and a second family adds a second injection.
- **`ViewOrgano` in `gal.conxugal.domain.organo`**
  ([ADR-0002](../../architecture/0002-hexagonal-architecture.md)) — one use case over
  `OrganoRepository.findById`, answering the Órgano or throwing the existing
  `OrganoNotFoundException`, in the shape `ListOrganos` already sets. The controller reaches no
  repository directly.
- **An unknown Órgano reuses `urn:conxugal:problem-type:organo-not-found`** through the existing
  `OrganoNotFoundExceptionHandler`, which moves out of `rest/admin/organos` to the shared
  `rest/error` package — the same move FEAT-0011's
  [TASK-0007](../FEAT-0011-contratos-menores-browsing/TASK-0007-paged-contracts-endpoint.md)
  records, done by whichever lands first. A **second** problem type for the same condition is not
  an option.
- **No per-reader scoping, deliberately.** An Órgano outside a `USER`'s visible set is not a 403
  and not a 404: visibility is a property of the contracts, not of the reader, so such an Órgano
  simply produces no family entry and answers `200` with `families: {}`. SPEC-0004 R9 scopes what
  is **listed**; SPEC-0005 does not make an Órgano's identity a secret.
- `@Secured(SecurityRule.IS_AUTHENTICATED)` — R2 grants the read to `USER` and `ADMIN` alike and
  denies an unauthenticated visitor
  ([ADR-0005](../../architecture/0005-session-based-authentication.md)), under the reserved
  `/api/` prefix ([ADR-0006](../../architecture/0006-reserved-api-url-prefix.md)).
- **Nothing about contracts.** No list, no paging, no row schema, no year parameter: this
  operation carries summaries and never a contract.
- Unit tests for the response assembly, integration tests in
  `server/application/src/integrationTest`, and the Schemathesis run
  ([ADR-0021](../../architecture/0021-openapi-contract-testing-with-schemathesis.md)) through
  `scripts/contract-test.sh`.

## What building it found

> **The handler moved, but not for the reason the contingency named.** Package-private visibility
> does *not* keep `OrganoNotFoundExceptionHandler` from applying outside `rest/admin/organos`:
> Micronaut resolves an `ExceptionHandler` by the exception type it is declared over, not by the
> package it sits in, and the 404 integration test passed with the handler still filed under
> `admin`. It moved to `rest/error` anyway, on the different question of where it *belongs* — two
> slices now throw the exception, and a handler co-located with one of them is owned by a caller
> that is not the only one. FEAT-0011's [TASK-0007](../FEAT-0011-contratos-menores-browsing/TASK-0007-paged-contracts-endpoint.md)
> records the same move; this task is the one that landed first, so that one inherits it done.
>
> **`families` is a record with one component, not a `Map`**, which carries
> `additionalProperties: false`'s meaning into Java. Choosing the record is also what made
> **both `@JsonInclude` overrides
> unnecessary**, and they are gone. The first draft carried `ALWAYS` on `OrganoMemberResponse` and
> `NON_NULL` on `FamiliesResponse`, each documented as load-bearing; neither was. Micronaut Serde's
> default `NON_EMPTY` asks a property's own serializer whether it is empty, and the one for a
> bean-typed property answers that only for a null — so `NON_EMPTY` degenerates to `NON_NULL`
> there. `families` is never null, and the absent family already is one. Deleting both annotations
> changes no byte of the payload, which is how it was checked. A `Map` would have been the case
> where `ALWAYS` was genuinely required, since a map serializer *does* report an empty map as
> empty; the `OrganoResponse` precedent the drafts cited is real but different, its `termoId` being
> an actual null. **What guards the shape is the round trip over HTTP**, not an annotation.
>
> **The member read costs two `findById` calls**, one in `ViewOrgano` and one inside
> `DescribeContratosMenoresSection`. It is the price of the port deciding its own presence rule —
> it answers an unknown Órgano exactly as it answers one holding nothing, and so cannot be what
> produces the 404. Left as it is: one extra indexed primary-key read, against a page that used to
> take three round trips.
>
> **The route became a field, and the contract lints clean.** A first draft keyed `families` by
> the child-route segment itself — `contratos-menores` — which made the mapping free but spelled a
> JSON property in kebab-case, the one thing `camel-case-properties` reports across the whole
> document. Carrying the segment as a `route` field instead keeps the mapping just as free while
> the key returns to camelCase: the client still derives nothing, because the server sends the
> segment either way. **The coupling was never removed by the first shape, only hidden in a
> convention** — this one states it. Together with the `$ref` that took away the
> `oas3-unused-component` warning TASK-0006 left behind, `scripts/openapi-lint.sh` now reports no
> warnings at all, and no rule had to be disabled to get there.

## Acceptance criteria

- An authenticated caller reading an Órgano that holds visible contratos menores receives its `id`,
  its `name`, and a `families` object whose `contratosMenores` entry carries the `route` its
  section is mounted at and a `summary` with the years newest first and R18's two flags.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #22 presence half, #26
  contract half, #43)
- An Órgano that holds no visible contratos menores answers `200` with `families` present and
  **empty** — serialised as `{}`, not omitted and not null — and with **no** `contratosMenores`
  key set to null. (SPEC-0005 #26 contract half, #49)
- An Órgano outside a `USER`'s visible set answers `200` with `families: {}` for that `USER`,
  neither `403` nor `404`. (SPEC-0005 #49)
- An unknown Órgano id answers `404` with `urn:conxugal:problem-type:organo-not-found`, the same
  type the existing operations use, and the contract declares no second type for it.
- An unauthenticated caller receives `401` and no data. (SPEC-0005 #2, #39 authentication half)
- The response carries no contract, no year selection, no paging envelope and no field naming the
  awarding Órgano; the operation takes no query parameter.
- Every response carries the three `RateLimit-*` headers, and the operation declares the shared
  `429`.
- `docs/api/openapi.yaml` declares the `contratosMenores` property by `$ref`, reaching
  `ContratosMenoresSummary` by `$ref` in turn and restating none of its fields;
  `scripts/openapi-lint.sh` passes and the Schemathesis run passes against the running instance.
