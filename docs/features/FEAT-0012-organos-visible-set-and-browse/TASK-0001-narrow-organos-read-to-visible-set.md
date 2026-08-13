---
feat: FEAT-0012
domain: backend
adrs: [0002, 0005, 0006, 0010, 0012, 0016, 0020, 0021]
status: done
depends_on: [TASK-0002]
---

# Narrow `GET /api/organos` to the visible set

The shipped catalogue read stops returning all 429 Órganos and returns the **visible set** —
every Órgano with at least one visible contract, in any family — derived when the catalogue is
read, through a port the contract side implements. Its shape, its Galician-collated ordering and
its `termoId` are untouched, so the tree callers build from it is assembled exactly as before,
from a shorter list.

**This is a change to shipped, contract-tested behaviour**, and it carries the rewrite of the
OpenAPI operation ([ADR-0010](../../architecture/0010-design-first-openapi-contract.md)) whose
description still asserts the rule
[SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) R1 revoked — *"reading
the catalogue is not an administration capability"* — and the reshaping of the integration test
named `user_reads_every_organo_with_its_name_state_and_placement`.

**`depends_on` points at a higher-numbered task on purpose.**
[TASK-0002](TASK-0002-administration-area-reads-admin-catalogue.md) moves the administration area
onto `GET /api/admin/organos`, and the feature records it as landing *with or before* this one:
narrowed first, the management tree, the Órgano table and the unclassified worklist silently lose
every Órgano without contracts — most of the catalogue, and precisely the ones an administrator
opens the section to file. Nothing errors, which is why the ordering is a dependency rather than
a note.

## Scope

- **The port, in `domain`** ([ADR-0002](../../architecture/0002-hexagonal-architecture.md)):
  `OrganosWithVisibleContracts` in `gal.conxugal.domain.organo`, one method
  `Set<OrganoId> among(Collection<OrganoId> candidates)`. **One implementation per contract
  family**, each defining *visible* for its own family; the catalogue read composes the answers
  and reaches into no family's tables. A new family joins by adding an implementation and
  changing nothing here — which is what keeps SPEC-0004 R9's *of any family* honest rather than
  aspirational.
  - It answers **a set for a set**, not a boolean per Órgano: the caller holds the whole
    catalogue already and one round trip per family is the whole cost.
- **The use case, in `domain`:** `ListVisibleOrganos`, taking `OrganoRepository` and
  `List<OrganosWithVisibleContracts>` — every implementation Micronaut finds — reading
  `findAllOrderByName()`, unioning the ports' answers and filtering the list **in place**, so the
  repository's collated order survives untouched. An empty catalogue calls no port.
- **`ListOrganos` is left exactly as it is**, and that is the load-bearing part of this task:
  `AdminOrganosController` reads the same use case, so narrowing it would narrow
  `GET /api/admin/organos` with it and delete R8's flat list — the opposite of what
  [TASK-0002](TASK-0002-administration-area-reads-admin-catalogue.md) is protecting. Two use
  cases, one repository read, no shared filter.
- **The contratos menores implementation, in `infrastructure`:**
  `JdbcContratoMenorRepository` also implements the port, backed by a `@Query`:

  ```sql
  SELECT DISTINCT organo_id FROM contrato_menor
   WHERE organo_id IN (:candidates)
     AND publication_date IS NOT NULL
     AND amount IS NOT NULL
     AND operador_economico_id IS NOT NULL
  ```

  - **All three columns, not two.**
    [SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) R28 withholds a contract
    missing **any** of its publication date, its amount or its awardee, and #50 says in as many
    words that such a contract *does not by itself place its Órgano in a `USER`'s visible set*.
    [FEAT-0011](../FEAT-0011-contratos-menores-browsing/README.md)'s indexes carry only the last
    two in their partial predicate because its reads add the year equality that implies the first;
    this read has no year, so it states the condition itself.
  - `contrato_menor_organo_id_publication_date_idx` (V13) leads with `organo_id` and serves it
    today; FEAT-0011's
    [TASK-0002](../FEAT-0011-contratos-menores-browsing/TASK-0002-visible-browse-schema-and-indexes.md)
    replaces that index with one that subsumes it. **Neither task blocks the other** — this query
    is written against the column, not the index.
  - An empty `candidates` short-circuits rather than emitting `IN ()`.
- **The controller:** `OrganosController.list()` calls `ListVisibleOrganos`; `/taxonomia` is
  untouched, `@Secured(SecurityRule.IS_AUTHENTICATED)`
  ([ADR-0005](../../architecture/0005-session-based-authentication.md)) is untouched, the path
  ([ADR-0006](../../architecture/0006-reserved-api-url-prefix.md),
  [ADR-0016](../../architecture/0016-rest-resource-naming.md),
  [ADR-0020](../../architecture/0020-actions-as-verbs-in-rest-paths.md)) is untouched and
  `OrganoResponse` is untouched. Its class javadoc stops describing the read as one table
  serialised row for row.
  - **The gate stays `IS_AUTHENTICATED` and the scoping is by surface, not by role**: an `ADMIN`
    calling this path gets the same narrowed set a `USER` gets, and reaches the whole catalogue by
    calling the `ADMIN`-gated read instead. A role check here would give one path two meanings.
- **The contract, rewritten** in `docs/api/openapi.yaml`: `listOrganos`' summary and description
  state that it serves the visible set — every Órgano with at least one visible contract, in any
  family — that the whole catalogue is `GET /api/admin/organos`, and that the narrowing applies to
  every caller. The `200` description keeps the collation guarantee and stops saying *the whole
  catalogue*. The preamble at the top of the file describing the Órganos reads is corrected with
  it. `AdminOrgano` and `GET /api/admin/organos` are not touched.
  [ADR-0012](../../architecture/0012-rate-limit-http-contract.md)'s headers and the shared
  responses are unchanged.
- **Tests:**
  - `ListVisibleOrganosTest` in `domain` — Mockito stubs for the repository and **two** port
    implementations, asserting the union of their answers and that catalogue order survives the
    filter;
  - an `infrastructure` integration test beside `JdbcContratoMenorRepositoryIntegrationTest`,
    against real PostgreSQL: an Órgano with one complete contract is answered; Órganos whose only
    contracts lack a date, an amount or an operador are not; an Órgano with no contracts is not;
    a candidate set that is empty, and one naming an Órgano with no rows, answer empty;
  - `OrganosControllerIntegrationTest` — `user_reads_every_organo_…` is replaced by cases for the
    narrowed read, and the existing `AdminOrganosControllerIntegrationTest` gains nothing: it
    already asserts the whole catalogue and must keep passing unchanged;
  - the Schemathesis run
    ([ADR-0021](../../architecture/0021-openapi-contract-testing-with-schemathesis.md)) through
    `scripts/contract-test.sh`.

## Acceptance criteria

- An authenticated caller reading `GET /api/organos` receives **only** Órganos holding at least
  one visible contract; an Órgano with none is absent from the response body, not merely hidden by
  a client. (SPEC-0004 #20; SPEC-0005 #48)
- An `ADMIN` and a `USER` calling `GET /api/organos` receive the **same** set — the scoping is the
  path's, not the caller's — while `GET /api/admin/organos` returns the whole catalogue to the
  `ADMIN`, in the same order, with its import mark and import state. (SPEC-0004 #8, #20, R9)
- An unauthenticated caller still receives `401` and no data. (SPEC-0004 #2, R2)
- Each returned Órgano carries the same `id`, `name`, `active` and `termoId` it did before, in the
  same Galician-collated name order; an inactive Órgano holding a visible contract is returned like
  any other, and an unclassified one is returned with a null `termoId`. (SPEC-0004 #19, #23)
- An Órgano whose only contratos menores each lack a publication date, an amount or an awardee is
  **absent** from the read. (SPEC-0005 #50)
- Storing an Órgano's **first** visible contrato menor makes it appear in the read with no
  administrator action and no import of the catalogue — proven by an integration test that inserts
  the contract and re-reads. (SPEC-0004 #21 entering half; SPEC-0005 #48)
  - **The leaving half is not provable here** and is not claimed: it needs a contract that can be
    made invisible, which is SPEC-0005 R13's removal, owned by the curation feature.
- The visible set is composed from **every** `OrganosWithVisibleContracts` bean: a domain test with
  two stubs asserts an Órgano answered by either one is included, and no production class outside
  `infrastructure` names a contract table or the contratos menores family.
- `ListOrganos` still answers the whole catalogue and `AdminOrganosControllerIntegrationTest`
  passes unchanged.
- `docs/api/openapi.yaml` no longer asserts that reading the catalogue is not an administration
  capability, and states where the whole catalogue is served; `scripts/openapi-lint.sh` passes and
  the Schemathesis run passes against the running instance.
- `./gradlew build`, `./gradlew :application:integrationTest` and
  `./gradlew :infrastructure:integrationTest` pass from `server/`.
