---
feat: FEAT-0009
domain: backend
adrs: [0002, 0011, 0014]
status: todo
depends_on: []
---

# `ContratoMenorSource` port + contratosdegalicia adapter

The port that answers one **(Órgano, three-month window, page)** slice — the only shape the
source offers — and the driven adapter behind it. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md) (the source is a driven adapter
behind a port), [ADR-0011](../../architecture/0011-blocking-io-virtual-threads.md) (blocking
I/O on virtual threads) and
[ADR-0014](../../architecture/0014-resilient-throttled-outbound-http-client.md) (the resilient,
self-throttling declarative client).

The contract it is written against is measured, not assumed:
[`design/source-contract.md`](design/source-contract.md).

**Prerequisite outside this feature:**
[FEAT-0006 TASK-0008](../FEAT-0006-organos-catalogue-import/TASK-0008-adopt-resilient-client-in-source-adapter.md)
moves the Órganos adapter onto the declarative client. Until it lands, that adapter still
injects a programmatic `@Named` client and the `RateLimiter`, `CircuitBreaker` and `Retry` this
adapter binds are **not actually shared** between the two — which is the whole basis for this
feature configuring no new rate. Build this task after it, or the sharing is aspirational.

## Scope
- `ContratoMenorSource` port in `domain`, answering **one slice per call**: given an Órgano's
  `sourceKey`, a from/to date window and a zero-based offset, it returns the rows of that page
  **and the source's `recordsTotal`** — the Órgano's whole contratos menores count, independent
  of the window. The walk is the use case's; the port neither iterates nor remembers.
- Each returned row is a source-entry value carrying the published fields verbatim — **including
  the awardee name and fiscal identifier**, which the contract no longer stores but
  [FEAT-0010](../FEAT-0010-operadores-economicos-base/README.md)'s derivation needs from the
  source row to match and to fill the operador's display fields:
  publication id, publication date text, object, amount, duration, awardee name, awardee fiscal
  identifier. No trimming, no case folding, no date parsing — interpretation happens above the
  port (R27).
- The adapter **declares a client interface** carrying `@ResilientClient` and
  `@Client(id = "contratosdegalicia")` — **the same id the Órganos adapter binds** — so both go
  through the one set of policies `ContratosDeGaliciaResilienceFactory` publishes per source.
  Given the `@Named`-qualifier bite this project has already taken, the id is stated here
  rather than left to be inferred. The adapter builds no request and holds no `HttpClient`; the
  ArchUnit rule FEAT-0006 TASK-0008 adds forbids it naming one.
- The request, exactly as measured:
  `GET api/v1/organismos/{organismo}/contratosmenores/table?datestart=&dateend=&start=&length=&draw=`,
  where `{organismo}` is **the `sourceKey` the catalogue already stores** — no new identifier to
  map. Unauthenticated `GET`; the response is JSON in **UTF-8**, unlike the ISO-8859-1 HTML the
  Órganos adapter reads, so the two adapters must not share a charset assumption.
- **The window limit is honoured by construction, never discovered.** The adapter rejects a
  window wider than three months, and a page size above 100, **without issuing a request** —
  an over-wide window answers a bare `500` with no machine-readable body, indistinguishable
  from a server fault, so relying on the error would feed the circuit breaker on a bug of ours.
- An unreachable source, or a response that cannot be read as the documented shape, surfaces as
  a **typed domain failure** (`ContratoMenorSourceUnavailableException`, the family's peer of
  `OrganoSourceUnavailableException`) rather than an empty success. Per ADR-0014, judging the
  *content* unusable is the adapter's own call and stays outside the breaker.
- An empty page is an **ordinary answer**, not a failure: a window before the Órgano's earliest
  publication answers `200` with `recordsFiltered: 0` and a `recordsTotal` that is still valid.

## Acceptance criteria
- Given a stubbed source, the adapter returns one window-page's rows with every published value
  intact — padded fiscal identifier and awardee name, the object at its published length, the
  date as `DD-MM-YYYY` text, the amount as a number — and accented text decoded from **UTF-8**
  without mojibake.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #40 as-published half)
- `recordsTotal` is surfaced alongside the rows on every response, including one whose window
  matched nothing — the precondition for #12's completeness test, which
  [TASK-0009](TASK-0009-single-organo-initial-import.md) proves.
- A window wider than three months, or a page size above 100, is refused by the adapter with no
  request issued at all — asserted against the stub receiving nothing. (The feature's
  *An over-wide window* edge case: it is a bug of ours, not a source condition, which is why it
  must never reach the source and be counted against the breaker.)
- An unreachable source, a non-2xx response and an unreadable body each surface as the port's
  typed failure, never as an empty page. (SPEC-0005 #36)
- A transient failure is retried and the call then succeeds, and the outbound request carries
  the shared policies — proven through the declarative client, not by the adapter calling one
  itself. (SPEC-0005 #38, initial-import mode only)
- Integration-tested against a **stubbed** source (WireMock) — no live network — and the
  adapter names no Micronaut HTTP client type.
