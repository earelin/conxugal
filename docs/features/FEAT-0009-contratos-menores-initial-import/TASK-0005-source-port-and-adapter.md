---
feat: FEAT-0009
domain: backend
adrs: [0002, 0011, 0014]
status: done
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

**Prerequisite outside this feature, already met:**
[FEAT-0006 TASK-0008](../FEAT-0006-organos-catalogue-import/TASK-0008-adopt-resilient-client-in-source-adapter.md)
has moved the Órganos adapter onto the declarative client, so the `RateLimiter`, `CircuitBreaker`
and `Retry` this adapter binds are shared with it in fact and not only on paper — which is the
whole basis for this feature configuring no new rate.

## Scope
- `ContratoMenorSource` port in `domain`, answering **one slice per call**: given an Órgano's
  `sourceKey`, a from/to date window and a zero-based offset, it returns the rows of that page
  **and the source's `recordsTotal`** — the Órgano's whole contratos menores count, independent
  of the window. The walk is the use case's; the port neither iterates nor remembers.
- Each returned row is a source-entry value carrying the published fields verbatim — **including
  the awardee name and fiscal identifier**, which the contract no longer stores but
  [FEAT-0010](../FEAT-0010-operadores-economicos-base/README.md)'s derivation needs from the
  source row to match on and to fill the operador's name:
  source id, publication date, object, amount, duration, awardee name, awardee fiscal
  identifier. The amount arrives as a JSON number and is carried as a `Money` (TASK-0003), at
  the scale the source published and with no rounding.
- **Every text field is trimmed of leading and trailing whitespace here, and nowhere else.** The
  source pads `nif` and `adjudicatario` out to fixed widths, and that padding is an artefact of
  its serialisation rather than anything it published (R27). Stripping it at the boundary is what
  keeps every consumer above the port — the aggregate, the operador derivation, the identifier
  canonicalisation —
  from each having to know the source pads. A field left **empty once trimmed** is carried as
  **absent**, not as an empty string, so there is one absent-value case above the port and not
  two.
- **The duration is capped at 64 characters here, in Java, before it can reach a column.** The
  source publishes short phrases in that field (`"1 mes"`), so the cap is generous and is not
  expected to fire; it exists so that an unexpectedly long value **loses its tail rather than
  failing the batch**, which would reject a real award and breach #42. Capping in Java rather
  than letting `VARCHAR(64)` reject the row is what makes that choice ours instead of the
  database's. The cap applies to the duration and to nothing else — `obxecto` has no bound
  anywhere.
- **Nothing else about the text is touched:** no case folding, no collapsing of internal runs of
  spaces, no punctuation stripped. Trimming and the duration cap are the narrowings R27 allows.
- **The publication date is the one value interpreted here**, because it is the one the aggregate
  does not store as text: the source's `DD-MM-YYYY` is parsed to a date and the text it arrived
  as is not retained, as the aggregate already records. Text that cannot be read as a date is
  carried as **absent** rather than failing the row — a real award is not refused over a date
  nobody can use.
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
  intact — the object at its published length however long, the `DD-MM-YYYY` publication date
  read as that date, the amount as a `Money` at its published scale — and accented text decoded
  from **UTF-8** without mojibake.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #40 as-published half)
- A publication date that cannot be read as `DD-MM-YYYY` is surfaced as **absent**, and the row
  is returned rather than refused.
- A row whose `nif` and `adjudicatario` arrive **space-padded** yields those values with the
  padding gone and **everything between the first and last non-space character untouched** —
  internal spacing, casing and punctuation all preserved. Asserted on a value with internal
  spaces, so trimming is distinguished from normalising rather than inferred from a single-word
  case passing. (SPEC-0005 #40, whitespace narrowing)
- A text field arriving **empty, or as whitespace only**, is surfaced as **absent** — the two
  reach consumers as one case, not as an empty string alongside a null.
- An object **longer than the sample row's 60 characters** round-trips at its full length, with
  no truncation anywhere in the adapter or its response binding. (The cap recorded in
  `design/source-contract.md` was wrong; this criterion is what stops it being re-introduced.)
- A duration **longer than 64 characters** comes back capped at 64, and one of exactly 64 comes
  back untouched — the boundary asserted on both sides, since an off-by-one here fails a batch at
  the column rather than showing up as a short string.
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
