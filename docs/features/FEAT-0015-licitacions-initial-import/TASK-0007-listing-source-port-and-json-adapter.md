---
feat: FEAT-0015
domain: backend
adrs: [0002, 0011, 0014]
status: done
depends_on: []
---

# `LicitacionListingSource` port and its JSON adapter

One **(Órgano, offset, order)** page of an Órgano's licitacións listing, over the shared
`contratosdegalicia` client. The cheap half of the retrieval: one call answers up to 100 entries and
the Órgano's `recordsTotal`, where the record half
([TASK-0008](TASK-0008-record-source-port-and-the-labelled-fields.md)) answers one procedure per
call.

**Two ports, not one**, because they are two mechanisms — one JSON, one HTML — and a single port
would hide from its caller that one call is a thousand times cheaper than the other.

Blocking I/O on virtual threads
([ADR-0011](../../architecture/0011-blocking-io-virtual-threads.md)) through the resilient,
self-throttling client of
[ADR-0014](../../architecture/0014-resilient-throttled-outbound-http-client.md). The port lives in
the domain, the adapter in `infrastructure`
([ADR-0002](../../architecture/0002-hexagonal-architecture.md)).

## Scope

- **`LicitacionListingSource`** — one method answering one page: the entries and `recordsTotal`.
  Modelled on `ContratoMenorSource` / `ContratoMenorSourcePage`, with a
  `LicitacionListingUnavailableException` named for its port, beside
  `LicitacionRecordUnavailableException` in TASK-0008.
- **A `LicitacionsClient` declarative client** at
  `/api/v1/organismos/{organismo}/licitaciones/table`, bound to `@Client(id = "contratosdegalicia")`
  and `@ResilientClient` — **the same client id** the contratos menores and Órganos clients bind, so
  all three are configured as one source. The R31 rate budget is one budget across every family and
  the catalogue import together, enforced by the advice's unqualified policy singletons rather than
  by the id, and this feature chooses no rate.
- **The full DataTables payload on every request, ordered or not.** The server resolves the order
  column **by name**, so `order[0][column]` alone answers `500` and only the request carrying every
  `columns[i][name]` answers `200`. The adapter has **no short form** and must not offer one as a
  shorthand — a caller that finds the abbreviated request working against a stub and failing against
  the source is the exact defect this rules out.
- **Order is a parameter, and it ships with one value: `id` ascending**, which is what an initial
  walk needs because it is stable under concurrent publication. The incremental feature will add
  `modificado` descending when it has a caller for it; per `CLAUDE.md` this task does not build a
  second value nobody uses.
- **A page above 100 rows is refused before a request is issued**, with `IllegalArgumentException`,
  on the shipped `ContratosDeGaliciaContratoMenorSourceAdapter.requireSliceWithinSourceLimits`
  precedent — whose own reasoning applies unchanged: an over-wide request answers a bare `500` with
  no machine-readable body, *"indistinguishable from a server fault — so a bug of ours would
  otherwise be counted against the source's health."* Refusing, not clamping.
- **Response fields** — `id`, `publicado`, `modificado`, `objeto`, `importe`, `estado`, `estadoDesc`
  — mapped to a source-entry record. This entry is what
  [TASK-0014](TASK-0014-reconciling-a-restated-procedure.md)'s `StoreLicitacion` takes alongside the
  parsed record, because four of the aggregate's fields exist only here.
- `publicado` and `modificado` are **`DD-MM-YYYY` text** and are interpreted here; one that cannot
  be read is surfaced as **absent** and the entry is still returned, since the column is nullable
  and R25's invisibility is a rule about readers.
- **`importe` is carried but named for what it is** — the base budget. It is *not* an awarded
  amount, and the entry's field name says so, because taking it for one would fill every R24 total
  and every operador history with budgets, silently and plausibly.
- The response is returned **whole** rather than as its body alone, on the `ContratosMenoresClient`
  precedent: a declarative client reads `404` as an absent value and hands back `null`, so an
  endpoint that moved would otherwise be indistinguishable from an Órgano publishing nothing.
- The response is **UTF-8**, which is Micronaut's default and unlike the record's ISO-8859-1.

**Out of scope:** the record retrieval, the walk, the cursor, the `estados` filter (measured to
work, wanted by nobody yet), and **correcting FEAT-0009's own source contract**, which still records
that ordering "was not made to work" — the feature names that as a follow-up it does not take.

## Acceptance criteria

- One page answers its entries and the Órgano's `recordsTotal`, and `recordsTotal` is read from
  **every** response rather than once, since it moves while a multi-hour import runs. *The walk that
  depends on this is [TASK-0015](TASK-0015-single-organo-initial-import.md)'s; here it is the port's
  shape.*
- The request the adapter issues carries **every** `columns[i][name]`, and `order[0][column]`
  resolving to the `id` column with `dir=asc` — asserted on the `LoggedRequest` the stub receives,
  because a request missing them answers `200` against a lenient stub and `500` against the source.
- The adapter returns the source's rows **in the order received**, re-sorting nothing, and offers no
  way to request a page without an explicit order.
- An Órgano answering `recordsTotal: 0` yields an empty page and no error.
- A page size above 100 is **refused with `IllegalArgumentException` and no request is issued** —
  asserted against the stub receiving nothing.
- An unreachable source, a non-`200`, and a body that will not parse each raise
  `LicitacionListingUnavailableException` rather than yielding an empty page. An empty page and a
  failed request must never be indistinguishable: one ends a walk and the other must not.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #41)
- A `publicado` or `modificado` that cannot be read as `DD-MM-YYYY` is surfaced as absent and the
  entry is still returned. (SPEC-0008 #44)
- The client binds `id = "contratosdegalicia"`, so it is configured as the same source as the
  contratos menores and Órganos clients, and a licitacións walk and a contratos menores import draw
  on **one** R31 rate budget. *The id is not what enforces that budget — the unqualified policy
  singletons the resilience advice injects are, so a client binding a different id would go on
  sharing this budget rather than getting one of its own. #42's measurement is taken by no task in
  this feature.*
- Integration-tested against a **WireMock** source on the
  `ContratosDeGaliciaContratoMenorSourceAdapterIntegrationTest` precedent — including the
  malformed-body, non-`200` and connection-fault cases — with no database.
