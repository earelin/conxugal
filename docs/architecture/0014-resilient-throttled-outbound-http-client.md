---
status: accepted
date: 2026-07-25
spec: null
supersedes: null
superseded_by: null
---

# 0014. Wrap outbound source calls in a retrying, self-throttling HTTP client

## Status
Accepted

## Context
Substantially all of conxugal's data comes from one external site it does not control,
contratosdegalicia.gal, and it will ask that site for several different things: a page
scraped for a catalogue, searches per query, result sets walked page by page, detail
documents per record. The request shapes and the parsing differ; the relationship does not.
We are an unattended automated client of somebody else's server, on a schedule, and that
server owes us nothing.

The client every one of those requests runs through — built by a `@Factory` in
`infrastructure` — sets a read timeout and nothing else. So a dropped connection or a
momentary 503 fails a whole run as surely as a missing page does, with nobody watching at
04:00; nothing paces us, which is survivable at one request per run and becomes an IP block
once a run issues one request per record; and we send no identifying `User-Agent`, leaving an
operator no way to contact us and no response available but blocking. That block would not be
scoped to whichever adapter earned it — one impolite caller costs every other caller the same
site, which is why this belongs to the client rather than to whoever uses it. It is a
cross-cutting concern on the driven side of [ADR-0002](0002-hexagonal-architecture.md)'s
hexagon, and it is far cheaper to settle now, with one adapter, than to retrofit across
several.

Two existing decisions constrain the options.
[ADR-0011](0011-blocking-io-virtual-threads.md) runs all I/O blocking on virtual threads, so a
thread that waits unmounts and costs almost nothing — blocking until a request is allowed is
the natural mechanism rather than reactive backpressure. And ingestion runs in one JVM
alongside the API, so a process-local budget is a correct total rather than an approximation.
This is not [ADR-0012](0012-rate-limit-http-contract.md), which governs the rate limit
conxugal *advertises to its own callers* on `/api/**`; the two share vocabulary and nothing
else.

One constraint on *how* the library is used. Resilience4j publishes a Micronaut integration
(`resilience4j-micronaut`) with annotations and AOP interceptors, but its current release,
2.4.0, still declares `io.micronaut.platform:micronaut-platform:4.1.6` — unchanged since
2.3.0 — against this project's Micronaut **5.0.2**. The skew is the integration's state, not
a lag a point release will close.

## Decision
Route every outbound call to an external source through a **resilience decorator in
`infrastructure` wrapping the Micronaut `BlockingHttpClient`**, built on **Resilience4j 2.4.0
used as a plain library** — the annotation module is not adopted, for the version reason
above. Only `resilience4j-retry`, `resilience4j-ratelimiter` and `resilience4j-circuitbreaker`
are declared, via `resilience4j-bom` so catalogue entries stay version-free. Configuration
lives in this project's own `@ConfigurationProperties` records, not the `resilience4j:` YAML
namespace, so there is one place to look for a knob.

**The three policies nest in a fixed order**, composed with the per-module statics rather than
the `Decorators` builder — that builder lives in the separate `resilience4j-all` artifact and
applies policies inside-out, so it reads in reverse of what it produces:

```java
Retry.decorateSupplier(retry,
    CircuitBreaker.decorateSupplier(breaker,
        RateLimiter.decorateSupplier(limiter, exchange)));
```

The order is load-bearing. The **limiter is innermost** so every attempt reaching the network
consumes a permit, retried attempts included — anywhere else, a retry storm outruns the pacing
exactly when the source can least absorb it. The **breaker is between**, so an open circuit
rejects a call before it burns rate budget. **Retry is outermost**, matching Resilience4j's own
aspect order.

That ordering has one cost, paid explicitly: the breaker encloses the limiter, so every
duration it records includes the permit wait. **This breaker therefore uses failure-rate
detection only.** Slow-call detection would instrument our own deliberate latency as a
symptom — any `slowCallDurationThreshold` below the permit wait holds the circuit open
forever — and adds no signal, since genuine slowness already becomes a *failure* via the
connect and read timeouts.

**The decorator sits below parsing, above transport.** Each adapter parses and validates what
it knows how to read, and the domain failure declared by its own port stays the only exception
escaping it.

**"Retryable" and "counts as a breaker failure" are independent.** A site defending itself
typically answers `200` with a block page: pointless to retry, essential to open the circuit.
So a call **carries the adapter's acceptability check and the decorator applies it** — a
response judged unusable is recorded against the breaker and is *not* retried. The check
travels inward rather than the breaker outward, so no adapter names a circuit breaker.

**No Resilience4j type crosses the decorator boundary, and no transport type reaches the
domain.** `RequestNotPermitted` and `CallNotPermittedException` are neither transport nor
domain failures and would otherwise pass through every adapter's `catch (HttpClientException)`
and surface from a port as raw library types. Translation happens in two hops, both inside
`infrastructure`: the decorator converts Resilience4j outcomes to the transport exception type
adapters already handle, and the adapter converts that to its port's domain failure as it does
today. Both types are also excluded from retry — retrying a refused permit or an open circuit
turns a deliberate fail-fast into a fail-slow.

**Adapters neither construct nor name an HTTP client.** Raw client construction moves to the
shared `infrastructure` HTTP package; adapters receive only the decorated client. A rule in
the `architecture` module — same idiom as [ADR-0006](0006-reserved-api-url-prefix.md)'s
URL-prefix rule, scoped to main sources — makes a reach for the raw client a build failure,
so bypassing the policy is unavailable rather than merely discouraged.

**Retryability is declared by the adapter, not inferred from the HTTP method.**
contratosdegalicia exposes searches over `POST` with the query in the body; those are pure
retrieval, and a method-based rule would exclude exactly the fan-out that motivates this
record. `GET` and `HEAD` are retryable by default; any other method only on an explicit
declaration, so the policy can never silently double-submit a state-changing call.

**Retryable failures are an explicit, closed set** — connection failures, resets, read
timeouts, and the transient statuses (`408`, `425`, `429`, `500`, `502`, `503`, `504`).
Everything else fails on the first attempt.

**`Retry-After` beats our backoff curve, up to a ceiling.** It is parsed in both forms RFC
9110 permits — `delay-seconds` and HTTP-date — since a date-form header met by integer parsing
throws from inside the retry machinery, and it is **clamped to a configured maximum**, beyond
which the call aborts rather than waits. A literal `Retry-After: 3600` met repeatedly would
otherwise turn one maintenance window into a run that looks alive for hours and returns
nothing. Absent the header, backoff is exponential **with jitter** — mandatory, because an
unjittered curve re-synchronises a fanned-out batch into a second burst.

**Every request carries an identifying `User-Agent`** naming the project and a contact URL,
set once in the decorator. An operator who can reach us can ask us to slow down; one who
cannot can only block us.

**Fan-out against a source is sequential by default.** `AtomicRateLimiter` does not queue
indefinitely: it computes the wait a caller needs given reservations already in flight and
**refuses outright** if that exceeds the maximum. So the maximum wait fixes a ceiling on
concurrent callers — `maxConcurrent × limitRefreshPeriod / limitForPeriod ≤ maxWait` — and
left implicit, a fanned-out batch would see a few dozen callers queue and the rest fail
instantly. At a queue depth of one the maximum wait need only cover a single cycle.
Concurrency may rise above one only where that inequality still holds, and under it a refused
permit means the configuration is inconsistent, so failing loudly is correct. Relatedly,
`limitForPeriod` is held at **1** with the pace set by `limitRefreshPeriod`: the limiter
refills its whole allowance at each boundary, so `10` per `10s` is ten simultaneous requests
then nine seconds of silence.

**All of it is configurable per source**: refresh interval, maximum wait, concurrency bound,
connect and read timeouts, retry attempts, backoff, the `Retry-After` clamp, and the breaker's
failure-rate threshold and open-state duration. The connect timeout is unset today and
inherits OS behaviour — now multiplied by the attempt count, and worst in the blackholed-IP
case this record exists to prevent. Exact defaults belong to the implementing task, but this
record fixes their ceiling: **at most one request in flight per source, and no faster than one
request per second**. Exceeding either is a new decision.

**No overall deadline is imposed on a single logical call.** Each wait is bounded but their sum
is not — worst case `attempts × (maxWait + connectTimeout + readTimeout) + Σ backoff`.
Enforcing a true ceiling means interrupting a blocking call in flight, which needs machinery
this deliberately blocking client does not have; the formula is recorded instead, and job-level
timeouts remain the scheduled trigger's business.

**Out of scope**, to be decided when needed: rate limiting shared across processes (a new
decision the day ingestion leaves one JVM), robots.txt, and exporting Resilience4j's metrics to
[ADR-0009](0009-sse-admin-realtime-metrics.md)'s admin panel.

## Consequences

### Pros
- Transient faults stop costing whole runs, and the benefit grows with every request a run
  makes.
- Pacing cannot be outrun by retries — the nesting makes that structural rather than something
  each adapter must be careful about.
- "Every adapter inherits the policy" is a property, not a hope: with the raw client
  unreachable from adapter packages and a build rule saying so, bypassing it is not a mistake
  someone can quietly make.
- The breaker can see the failure that matters — a source blocking us behind a `200`
  eventually opens the circuit instead of being absorbed as a content error.
- Being identified and self-throttling makes us a client an operator can *manage* rather than
  one they can only block, which matters more than any particular rate value.
- The domain never names a transport or resilience type, so tuning or replacing the library is
  invisible to it.

### Cons
- A **third-party dependency outside the Micronaut platform BOM**, used in a way its own
  Micronaut documentation does not describe. The programmatic API is the library's stable core,
  but we forgo the annotation ergonomics and inherit no upstream guidance for this combination.
- **The breaker is close to inert until more request kinds exist** — a single-request run never
  reaches a meaningful `minimumNumberOfCalls`, and open-state duration is meaningless across
  runs a day apart. Until then it is machinery that earns nothing.
- **A sequential default buys politeness with wall-clock time** — hours, not minutes, at
  thousands of requests — and the budget is shared, so a run walking result pages and a run
  fetching details compete for it rather than each getting one.
- **Rate, concurrency and maximum wait are coupled**, and a configuration violating the
  inequality fails not at startup but as refused permits under load. Nothing validates the
  three together; review is all that enforces it. Likewise nothing bounds a whole logical call.
- Retry outermost records **several** breaker failures per logical failure — right for an
  outbound client, but the thresholds only make sense read that way.
- **Errors are translated twice**, which is what isolates the domain but also puts a failure's
  cause two hops from where it is read.
- **What is reusable is the policy, not the wiring.** Each source still declares its own
  configuration record and factory. `@EachProperty`/`@EachBean` would go further but is
  unverified against records on this framework version and would displace the `@Named`
  qualifier pattern this project has already been bitten by.
- **Adapters now carry a judgement with weight**: an acceptability check too strict opens the
  circuit against a merely unusual response, too lax leaves the breaker as blind as before.
- Initial rate, backoff and breaker settings are **educated guesses** — the source publishes no
  limits — and the decorator invalidates the existing unit tests that stub
  `HttpClient.toBlocking()` directly.
