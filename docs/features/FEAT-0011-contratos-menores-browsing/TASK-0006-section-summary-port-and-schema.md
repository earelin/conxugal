---
feat: FEAT-0011
domain: backend
adrs: [0002, 0010]
status: todo
depends_on: [TASK-0004]
---

# The contratos menores summary: the port FEAT-0013 calls, and the schema it publishes

What a contratos menores section says about itself — which years it offers, whether what is shown
is partial, and whether the Órgano is still being updated — made available as a **family summary**
that [FEAT-0013](../FEAT-0013-organo-contracts-page/README.md)'s `GET /api/organo/{id}` carries
as the `contratos-menores` entry of its `families` map.

**This task ships no endpoint.** One feature owns the shape of what a family says about itself;
another owns the page that asks every family. That is why a new family later adds a property to a
map rather than an endpoint to the contract — and it is why an earlier draft's
`…/contratos-menores/resumo` is gone: it was a correct endpoint and one request too many.

## Scope

- **The driving port**, in the `domain`: `DescribeContratosMenoresSection` from
  [TASK-0004](TASK-0004-year-facets-and-section-state.md) is what FEAT-0013's controller injects.
  Its `Optional.empty()` for an Órgano with no visible contracts of this family is the whole
  mechanism by which **no entry appears and no tab is drawn** — this task adds no second way to
  say the same thing.
- **The wire shape**, in the `application` module beside the other REST responses: a
  `ContratosMenoresSummaryResponse` record with `years`, `partial` and `updating`, and a static
  factory mapping the domain section onto it. It lives here rather than in FEAT-0013's package so
  that the feature owning the family owns its serialisation; FEAT-0013 composes it and knows
  nothing of how a year facet becomes JSON.
- **The named OpenAPI schema**, authored contract-first
  ([ADR-0010](../../architecture/0010-design-first-openapi-contract.md)): a
  `ContratosMenoresSummary` under `components.schemas` in `docs/api/openapi.yaml`, with `years`
  (an array of four-digit years, newest first, never empty), `partial` and `updating`, all three
  required, and a description recording that its **presence** in the families map is what says the
  section exists.
  - It is declared here and `$ref`-ed by
    [FEAT-0013](../FEAT-0013-organo-contracts-page/README.md)'s member read, so one feature
    declares it and one endpoint serves it. Until that lands the schema is referenced by nothing;
    `scripts/openapi-lint.sh` must still pass, and if vacuum's unused-component rule fails the
    gate rather than warning, the schema moves into FEAT-0013's task with a note here rather than
    being restated in two places.
- **What is deliberately absent**: no controller, no route, no `@Secured` annotation, no problem
  type. Nothing about the import beyond the two flags, and nothing added to `GET /api/organos`.
- Unit-tested: the mapping from a domain section onto the response, including that `years` keeps
  the newest-first order it arrived in and that both flags survive independently.

## Acceptance criteria

- A `contratos-menores` summary can be obtained for an Órgano with visible contracts, carrying its
  years newest first and R18's two flags; an Órgano with none produces **nothing at all** rather
  than an empty summary.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #26, contract half)
- `years` is never empty in a produced summary, and offers only years — no *all years* and no
  *undated* member is representable in the schema. (SPEC-0005 #43)
- `partial` and `updating` are two independent booleans on the wire, and the schema offers no
  combined status field. (SPEC-0005 #26)
- `docs/api/openapi.yaml` declares `ContratosMenoresSummary` once, and this task adds **no path**
  to the contract.
- `scripts/openapi-lint.sh` passes.
- The mapping is unit-tested; no integration test is added, because there is no endpoint to call.
