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
a lag a point release will close. What that module offers, though, is not the policies —
those are in the plain library — but the *ergonomics*: annotate a method, get the policy.
Micronaut's own AOP gives us the same thing directly, and the framework applies around advice
to a declarative `@Client` interface: introduction advice implements the method, and any
around advice on it runs outside that implementation.

## Decision
Reach every external source through a **Micronaut declarative `@Client` interface carrying our
own `@ResilientClient` around advice**, built on **Resilience4j 2.4.0 used as a plain library** —
`resilience4j-micronaut` is not adopted, for the version reason above, and its annotation
ergonomics are reproduced by one annotation and one `MethodInterceptor` we own. Only
`resilience4j-retry`, `resilience4j-ratelimiter` and `resilience4j-circuitbreaker` are declared,
via `resilience4j-bom` so catalogue entries stay version-free. The `resilience4j:` YAML namespace
is not used; policy settings bind to this project's own `@ConfigurationProperties` record, and a
`@Factory` turns that record into the `Retry`, `RateLimiter` and `CircuitBreaker` beans the
interceptor holds.

**An adapter declares an interface, not a request.** It writes the shape of the call — path,
method, parameters, return type — and Micronaut generates the client at compile time. Nothing in
an adapter builds a request, holds a client, or names an HTTP client type; the advice on the
interface is what makes the call resilient, so getting the policy is the same act as declaring
the call.

**Configuration is split by who owns it.** Transport settings — base URL, connect and read
timeouts — live under `micronaut.http.services.<id>`, which `@Client(id = "<id>")` binds
natively. Policy settings live under this project's `conxugal.` namespace. Two places to look
rather than one: the price of letting the framework build the client instead of hand-rolling a
`@Factory` for it, and cheap next to the wiring that buys.

**The three policies nest in a fixed order**, composed in the interceptor around the intercepted
call with the per-module statics rather than the `Decorators` builder — that builder lives in the
separate `resilience4j-all` artifact and applies policies inside-out, so it reads in reverse of
what it produces:

```java
Retry.decorateSupplier(retry,
    CircuitBreaker.decorateSupplier(breaker,
        RateLimiter.decorateSupplier(limiter, context::proceed)));
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

**The advice sits below parsing, above transport.** It wraps the generated client method, so
response binding happens inside it and an adapter's own parsing and validation happen outside.
Each adapter interprets what it knows how to read, and the domain failure declared by its own
port stays the only exception escaping it.

**Response *content* is not the policy's business.** A site defending itself typically answers
`200` with a block page — pointless to retry, and something a breaker would ideally see. It
will not: the advice wraps the call, and whatever judges the body sits outside it, so a block
page that parses is indistinguishable from a real answer. Recovering that signal means handing
the interceptor a content predicate, which is precisely the coupling declarative clients remove.
The breaker therefore watches transport outcomes and status codes only, and an adapter's
"this response is unusable" judgement stays an ordinary domain failure. This is a deliberate
narrowing of what the breaker can detect, taken because the alternative reintroduces a bespoke
call signature to every adapter.

**No Resilience4j type crosses the advice boundary, and no transport type reaches the domain.**
`RequestNotPermitted` and `CallNotPermittedException` are neither transport nor domain failures
and would otherwise pass through every adapter's `catch (HttpClientException)` and surface from
a port as raw library types. Translation happens in two hops, both inside `infrastructure`: the
interceptor converts Resilience4j outcomes to the transport exception type adapters already
handle, and the adapter converts that to its port's domain failure as it does today. Both types
are also excluded from retry — retrying a refused permit or an open circuit turns a deliberate
fail-fast into a fail-slow.

**Adapters neither construct nor name an HTTP client.** With the client generated from an
interface, no `infrastructure` class outside its own declaration mentions `HttpClient` or
`BlockingHttpClient` at all. A rule in the `architecture` module — same idiom as
[ADR-0006](0006-reserved-api-url-prefix.md)'s URL-prefix rule, scoped to main sources — makes
naming either type a build failure, so hand-building an unpoliced client is unavailable rather
than merely discouraged.

**Retryability is read from the method's own annotations, not inferred from a request object.**
`@Get` and `@Head` are retryable by default; any other method only where the declaration says
`@ResilientClient(idempotent = true)`, so the policy can never silently double-submit a
state-changing call. This matters because contratosdegalicia exposes searches over `POST` with
the query in the body: pure retrieval, and a method-based rule alone would exclude exactly the
fan-out that motivates this record.

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
declared **on the advice annotation itself** as a `@Header` stereotype: a client that carries the
policy carries the identification, with no declaration of its own and no way to have one without
the other. Since an annotation value must be a constant or a placeholder, and the running version
is neither until the process starts, the value is a placeholder and an `ApplicationContextConfigurer`
publishes the property. An operator who can reach us can ask us to slow down; one who cannot can
only block us.

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
retry attempts, backoff, the `Retry-After` clamp, and the breaker's failure-rate threshold and
open-state duration under `conxugal.`; connect and read timeouts under the source's
`micronaut.http.services` entry. The connect timeout is unset today and inherits OS behaviour —
which retry multiplies by the attempt count, and which is worst in the blackholed-IP case this
record exists to prevent — so it becomes an explicit setting. Exact defaults belong to the
implementing task, but this record fixes their ceiling: **at most one request in flight per
source, and no faster than one request per second**. Exceeding either is a new decision.

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
- "Every adapter inherits the policy" is a property, not a hope: with no client to hand-build
  and a build rule saying so, bypassing it is not a mistake someone can quietly make.
- **An adapter shrinks to the shape of its call.** Path, method, parameters and return type on
  an interface; no request building, no client held, no transport error handling beyond the
  translation its port already does. Adding the next source request is a method signature.
- The resilience wiring is written **once**, not once per call site, and the composition order
  that makes it correct lives in one interceptor instead of being restated wherever a request
  is made.
- Being identified and self-throttling makes us a client an operator can *manage* rather than
  one they can only block, which matters more than any particular rate value.
- The domain never names a transport or resilience type, so tuning or replacing the library is
  invisible to it.

### Cons
- **The breaker is blind to a source that blocks us behind a `200`.** The clearest signal a
  defending site emits is the one this design cannot see, because content judgement sits outside
  the advice. Such a run fails on the adapter's own validation, one run at a time, without the
  circuit ever opening. Recovering it means either a content predicate threaded through the
  interceptor or a second interceptor reading a marker exception — both re-adding the coupling
  declarative clients removed, and neither worth it until a source actually does this to us.
- A **third-party dependency outside the Micronaut platform BOM**, used in a way its own
  Micronaut documentation does not describe. The programmatic API is the library's stable core,
  but we forgo the annotation module and inherit no upstream guidance for this combination.
- **We own an AOP interceptor**, which is framework-level code with framework-level failure
  modes: advice silently not applied because an annotation went on the wrong element, or a
  future non-blocking return type passing through unpoliced. Cheap to write, and not the kind of
  thing whose breakage announces itself — it needs a test that asserts the advice is *on*.
- **Configuration lives in two namespaces**, `micronaut.http.services` for transport and
  `conxugal.` for policy, so no single block shows how a source is treated.
- **The breaker is close to inert until more request kinds exist** — a single-request run never
  reaches a meaningful `minimumNumberOfCalls`, and open-state duration is meaningless across
  runs a day apart. Until then it is machinery that earns nothing.
- **A sequential default buys politeness with wall-clock time** — hours, not minutes, at
  thousands of requests — and the budget is shared, so a run walking result pages and a run
  fetching details compete for it rather than each getting one.
- **Rate, concurrency and maximum wait are coupled.** The configuration record rejects a
  combination that violates the inequality when it binds, rather than letting it surface as
  refused permits under load — but that check lives in each source's own record, so a second
  source can omit it and inherit the original footgun. Nothing bounds a whole logical call.
- Retry outermost records **several** breaker failures per logical failure — right for an
  outbound client, but the thresholds only make sense read that way.
- **Errors are translated twice**, which is what isolates the domain but also puts a failure's
  cause two hops from where it is read.
- **What is reusable is the policy, not the wiring.** Each source still declares its own
  configuration record and factory. `@EachProperty`/`@EachBean` would go further but is
  unverified against records on this framework version and would displace the `@Named`
  qualifier pattern this project has already been bitten by.
- Initial rate, backoff and breaker settings are **educated guesses** — the source publishes no
  limits — and moving to a generated client invalidates the existing adapter unit tests, which
  stub `HttpClient.toBlocking()` directly.
