---
status: proposed
date: 2026-07-25
spec: null
supersedes: null
superseded_by: null
---

# 0014. Wrap outbound source calls in a retrying, self-throttling HTTP client

## Status
Proposed

## Context
Substantially all of conxugal's data comes from one external site it does not control,
contratosdegalicia.gal, and it will ask that site for several different things: a page
scraped for a catalogue, searches issued per query, result sets walked page by page, detail
documents fetched one per record. What differs between those is the request shape and the
parsing behind it. What does not differ is the relationship — we are an automated client of
somebody else's server, repeatedly, unattended, on a schedule, and that server owes us
nothing.

The outbound client every one of those requests runs through — built by a `@Factory` in
`infrastructure` and today driven by a single source adapter — sets a read timeout and
nothing else. Three gaps follow, and all three get worse rather than better as the number of
request kinds grows.

**Transient faults are indistinguishable from permanent ones.** A dropped connection, a
momentary 503 during the source's own maintenance window, or a read timeout under load all
surface as the same typed failure as a genuinely missing page. Once these runs happen
unattended and overnight, a single packet loss at 04:00 costs a whole run with nobody
watching — and the more requests a run makes, the likelier it is that at least one of them
meets a blip. The cheapest correct answer — try again in a moment — is not available
anywhere in the stack.

**Nothing paces us.** Today there is one request per run, so the site cannot tell us apart
from a browser. That is temporary and not a design we chose: as soon as a run issues a
request per record or per result page, it becomes hundreds to thousands of requests fired as
fast as the JVM can make them, at whatever hour the scheduler starts. A public administration
site with no published rate limits does not answer that with a `429`; it answers with an IP
block, and we find out days later when data silently stopped arriving. Crucially, that block
is not scoped to the request kind that caused it — one impolite adapter costs us every other
adapter too, which is why pacing has to be a property of the client rather than of whoever
happens to be using it.

**We are anonymous.** The client sends no identifying `User-Agent`. An operator looking at
their access log sees unattributed automated traffic with no way to contact whoever is
generating it. Their only available response is to block it.

None of this is specific to any one adapter or any one kind of request, and none of it is
worth deciding more than once. Every driven adapter that speaks HTTP to an external source
faces the same three gaps and should inherit the same answer, which makes this a
cross-cutting pattern on the driven side of
[ADR-0002](0002-hexagonal-architecture.md)'s hexagon. It is also cheapest to settle now,
while one adapter exists: the policy costs little to establish once and a great deal to
retrofit across several.

Two existing decisions shape the available options.
[ADR-0011](0011-blocking-io-virtual-threads.md) already runs all I/O blocking on virtual
threads, explicitly naming outbound scraping as a motivating case: a thread that *waits*
unmounts and costs almost nothing, so blocking until a request is allowed to proceed is the
natural mechanism here rather than reactive backpressure. And the ingestion jobs run
in-process alongside the API in one JVM, so a process-local budget is a correct
implementation of "our total outbound rate", not an approximation of one.

This must not be confused with [ADR-0012](0012-rate-limit-http-contract.md), which points
the other way: that decision governs the rate limit **conxugal advertises to its own
callers** on `/api/**`, in `RateLimit-*` headers and a `429` response. This ADR governs how
conxugal behaves **as a client of somebody else's server**. They share vocabulary and
nothing else.

Finally, a constraint on *how* the chosen library is used.
[Resilience4j](https://resilience4j.readme.io/) publishes a Micronaut integration
(`resilience4j-micronaut`) offering `@Retry`, `@RateLimiter` and `@CircuitBreaker`
annotations with AOP interceptors and a `resilience4j:` YAML namespace. Its current release,
2.4.0, declares `io.micronaut.platform:micronaut-platform:4.1.6` — unchanged since 2.3.0 —
and depends on Micronaut 4 internals including `micronaut-retry` and
`micronaut-discovery-core`. This project runs Micronaut **5.0.2**, a major version ahead
across a line that changed annotation processing and AOP internals, so the skew is the
integration's current state rather than a lag a point release will close. Adopting the
annotation module would drag a stale platform BOM into the dependency graph in exchange for
interceptors that are not validated against the framework version we run.

## Decision
Route every outbound call to an external source through a **resilience decorator in
`infrastructure` that wraps the Micronaut `BlockingHttpClient`**, built on **Resilience4j
2.4.0 used as a plain library**.

**Resilience4j is consumed programmatically, not through its Micronaut starter.** The
`resilience4j-bom` is imported so individual module entries in the version catalogue stay
version-free, matching how the Micronaut platform BOM is already used; only
`resilience4j-retry`, `resilience4j-ratelimiter` and `resilience4j-circuitbreaker` are
declared. The `resilience4j-micronaut` annotation module is **not** adopted, for the version
reason above. Configuration comes from this project's own `@ConfigurationProperties` records
rather than the library's `resilience4j:` YAML namespace, so there remains exactly one place
to look for a knob.

**The three policies are composed longhand, not with the `Decorators` builder.** That
builder lives in the separate `resilience4j-all` artifact, which would pull in modules —
bulkhead, time limiter, cache, Vavr — that this decision does not use; and its call sequence
applies policies inside-out, so the code reads in the reverse of the nesting it produces.
Composing with the per-module statics instead keeps the dependency set at three modules and
makes the order textually match the diagram below:

```java
Retry.decorateSupplier(retry,
    CircuitBreaker.decorateSupplier(breaker,
        RateLimiter.decorateSupplier(limiter, exchange)));
```

**The three policies nest in a fixed order: retry outermost, rate limiter innermost.**

```mermaid
flowchart TB
    adapter["Source adapter — parses and validates the response"]
    retry["Retry — backoff with jitter, honours Retry-After"]
    breaker["CircuitBreaker — trips on sustained failure"]
    limiter["RateLimiter — waits for a permit within the configured maximum"]
    exchange["HTTP exchange — adds the identifying User-Agent"]
    source["contratosdegalicia.gal"]

    adapter --> retry --> breaker --> limiter --> exchange --> source
```

The order is load-bearing, not incidental:

- The **rate limiter is innermost** so that *every* attempt that reaches the network
  consumes a permit, retried attempts included. Any other position lets a retry storm
  outrun the pacing at exactly the moment the source is least able to absorb it — the
  failure mode this decision exists to prevent.
- The **circuit breaker sits between them** so that an open circuit rejects a call *before*
  it consumes rate budget, and so the breaker's window records real network attempts rather
  than retry bookkeeping.
- **Retry is outermost**, which is also Resilience4j's own aspect ordering, so a retried
  attempt re-enters the breaker and re-acquires a permit rather than bypassing either.

That ordering has one cost, and it is paid explicitly: because the breaker encloses the
limiter, **every duration the breaker records includes the time spent waiting for a permit**.
So **this breaker uses failure-rate detection only; slow-call detection is deliberately not
used.** A breaker asking "was this call slow?" through a component whose entire job is to
make calls slow would be instrumenting our own design as though it were a symptom — set
`slowCallDurationThreshold` anywhere below the permit wait and a correctly functioning rate
limiter produces a permanent 100% slow-call rate and a circuit that never closes. It would
also add no signal: genuine slowness is already bounded by the connect and read timeouts,
which turn a hung call into a *failure* that failure-rate detection catches. This matches
Resilience4j's defaults, and is recorded so that enabling it later is understood as
reopening this decision rather than tuning a knob.

**The decorator sits below parsing, above transport.** Source adapters keep the contract
they have today: each parses and validates the response it knows how to read, and the domain
failure declared by its own port remains the only exception type that escapes it. Adapters
differ in what they request and how they interpret it; they do not differ in how they treat
the source, and that is the part this decorator owns.

**"Retryable" and "counts as a breaker failure" are separate properties, and a response can
be the second without being the first.** A source that answers successfully with the wrong
content is not having a transient fault, so retrying it is pointless — but it does not follow
that the breaker should record it as a success. The case that matters is the one this whole
record exists for: a site defending itself against a scraper typically answers `200` with a
block or challenge page rather than `429`. Judged only on status that is a success; judged on
content it is the source telling us to stop. Treating it as a plain content error would leave
the breaker blind to the single signal it most needs, and under fan-out we would keep
hammering a site that has already refused us.

So a call **carries the adapter's acceptability check, and the decorator applies it**. A
response the adapter judges unusable — a list implausibly shorter than the source could hold,
a result page missing the structure it must have, a detail document that parses to nothing —
is recorded against the breaker as a failure and is **not** retried.
The check travels into the decorator rather than the breaker travelling out to the adapter:
the adapter supplies a judgement about its own payload, which is knowledge it alone has,
while Resilience4j stays wholly inside the decorator and no adapter names a circuit breaker.

**No Resilience4j type crosses the decorator boundary, and no transport type reaches the
domain.** The library signals its own outcomes with exceptions that are neither transport
failures nor domain failures: `RequestNotPermitted` when a permit is refused,
`CallNotPermittedException` when the circuit is open. Left alone they pass straight through
an adapter's existing `catch (HttpClientException)` and surface from a domain port as raw
library types — the driven-side leak [ADR-0002](0002-hexagonal-architecture.md) forbids, and
the two conditions most likely to arise once this ships. Translation therefore happens in two
hops, both inside `infrastructure`: the **decorator** converts every Resilience4j outcome
into the transport exception type adapters already handle, and the **adapter** converts that,
as it does today, into its port's domain failure. The domain stays isolated from HTTP error
handling entirely — it names neither a Micronaut HTTP type nor a Resilience4j one, which is
the purity rule the ArchUnit module already enforces. Adapters learn no new vocabulary, and a
new adapter cannot forget a translation step that happens beneath it.

**A refused permit and an open circuit are never retried.** Retrying either re-enters the
policy that just rejected the call, converting a deliberate fail-fast into a fail-slow that
waits out the whole backoff schedule before reporting what was known at the first attempt.

**Adapters do not construct or name an HTTP client, and an ArchUnit rule enforces it.**
Raw client construction moves into the shared `infrastructure` HTTP package; every source
adapter receives the decorated client and never sees `HttpClient` or `BlockingHttpClient`.
Without this, "every adapter inherits the policy" would be a hope rather than a property —
a new adapter calling `HttpClient.create(url)` would compile, pass every test, and reach the
source with no pacing, no retry and no breaker, and nothing would say so. Structuring it this
way makes the bypass unavailable rather than merely discouraged, and a rule in the
`architecture` module — in the same idiom as the existing module-boundary rules and
[ADR-0006](0006-reserved-api-url-prefix.md)'s URL-prefix rule, and scoped to main sources so
tests may still drive clients directly — turns an adapter that reaches for the raw client
into a build failure.

**Retryability is declared by the adapter, not inferred from the HTTP method.**
contratosdegalicia exposes its contract searches over `POST` with the query in the request
body; those are pure retrieval and safe to repeat, and a method-based rule would exclude
precisely the fan-out that motivates this decision. So: `GET` and `HEAD` are retryable by
default, and any other method is retried **only** when the adapter issuing it explicitly
declares the request idempotent. A non-safe request carrying no such declaration is never
retried, so the policy can never silently double-submit a future state-changing call.

**Retryable failures are named explicitly**: connection failures, resets and read timeouts,
plus HTTP `408`, `425`, `429`, `500`, `502`, `503` and `504`. Every other response —
`400`, `401`, `403`, `404`, `410` — is permanent and fails on the first attempt. Retrying a
`404` only wastes the source's capacity to keep answering `404`.

**The source's own instruction beats our backoff curve, up to a ceiling.** When a `429` or
`503` carries `Retry-After`, that value determines the wait, via Resilience4j's
`intervalBiFunction` reading the header off the failed response. Two things about that
header are decided here rather than left to discovery. It is parsed in **both** forms RFC
9110 permits — `delay-seconds` and an HTTP-date, the latter via Micronaut's
`HttpHeaders.findDate` — because a date-form header met by integer parsing throws from
inside the retry machinery, where nothing is positioned to handle it. And the value is
**clamped to a configured maximum**: a source answering `Retry-After: 3600` during a
maintenance window is being honest, but honouring it literally parks an overnight run for an
hour per attempt — and a run that makes many requests can meet that header repeatedly,
turning one maintenance window into a job that looks alive for hours and returns nothing. Beyond the clamp the call **aborts rather than waits**, so the run fails while
someone can still read the log and understand why. Absent the header, the wait is
exponential backoff **with jitter** — jitter is mandatory, not a refinement, because an
unjittered curve re-synchronises a fanned-out batch into a second simultaneous burst.

**Every request carries an identifying `User-Agent`** naming the project and a contact URL,
set once in the decorator so no adapter can forget it. An operator who can see who we are
and reach us has the option of asking us to slow down; an operator who cannot has only the
option of blocking us.

**Waiting for a permit is the normal path, and outbound concurrency per source is bounded
so that waiting stays possible.** `AtomicRateLimiter` does not queue callers indefinitely
and then time out. It computes the wait a caller would need, given the reservations already
made by everyone else in flight, and if that wait exceeds the configured maximum it
**refuses immediately** rather than waiting at all. So the maximum wait is not merely a
patience setting — with a rate of `limitForPeriod` per `limitRefreshPeriod` it fixes a hard
ceiling on how many callers can be in flight before new ones start failing outright:

```
maxConcurrent × limitRefreshPeriod / limitForPeriod ≤ maxWait
```

Left implicit, that ceiling turns the motivating scenario into a defect: a few dozen callers
would queue and the rest of a fanned-out batch would fail instantly with a refused permit,
producing a failed run instead of a slow, polite one. So **fan-out against a source is
sequential by default** — one request in flight, the queue depth is one, and the maximum
wait only ever has to cover a single refresh cycle. A source may raise its concurrency above
one only where the inequality above still holds, which the implementing configuration must
satisfy explicitly rather than by hope. Under this bound a refused permit is no longer a
routine outcome; it means the configuration itself is inconsistent, and failing the run
loudly is the correct response.

**The rate is expressed as one request per interval, not as a quantum over a window.**
`AtomicRateLimiter` refills its whole allowance at each refresh boundary, so `10` per `10s`
permits ten simultaneous requests and then nine seconds of silence — a burst wearing a rate
limit's clothing, and indistinguishable from an attack in the only place it matters, the
operator's access log. Configurations therefore hold `limitForPeriod` at **1** and set the
pace with `limitRefreshPeriod`.

**All of it is configurable per source**: the refresh interval, the maximum wait for a
permit, the concurrency bound, connect and read timeouts, retry attempts, initial backoff and
multiplier, the `Retry-After` clamp, and the breaker's failure-rate threshold and open-state
duration. The connect timeout in particular is currently unset and inherits whatever the OS
does — which after this decision is multiplied by the attempt count, and which bites hardest
in the blackholed-IP case that is the exact symptom of the blocking this record exists to
prevent. Exact defaults belong to the implementing task — the site publishes no limits, so the first values are
estimates to be tuned against observed behaviour without a code change — but this record
fixes their ceiling: **at most one request in flight per source, and a default rate no
faster than one request per second**. Tuning below those bounds is a task's business;
exceeding either is a new decision.

**No overall deadline is imposed on a single logical call.** Each individual wait is bounded
— permit, connect, read, backoff, `Retry-After` clamp — but their sum is not, and in the
worst case one logical call costs roughly
`attempts × (maxWait + connectTimeout + readTimeout) + Σ backoff`. Enforcing a true ceiling
would mean interrupting a blocking call in flight, which needs an executor and a future that
this deliberately blocking client does not have; adding that machinery to solve a budgeting
problem is the wrong layer. The formula is recorded instead so that whoever tunes the knobs
can see what they are buying, and any job-level timeout stays the scheduled trigger's own
responsibility.

**Deliberately out of scope**, to be decided if and when they are needed: rate limiting
shared across processes (irrelevant while ingestion runs in one JVM, and a new decision the
day it does not), robots.txt fetching and interpretation, and exporting Resilience4j's
metrics — `resilience4j-micrometer` is the obvious later feed for
[ADR-0009](0009-sse-admin-realtime-metrics.md)'s admin metrics, but wiring it is not decided
here.

## Consequences

### Pros
- Transient faults stop costing whole overnight runs. The most common real failure — a
  momentary network or 5xx blip — is absorbed silently instead of producing stale data and a
  morning investigation, and the benefit grows with every additional request a run makes.
- The pacing cannot be outrun by retries. Because the limiter is innermost, the ordering
  makes that a structural property of the composition rather than something each adapter
  has to be careful about.
- Being identified and self-throttling makes us a client an operator can *manage* rather
  than one they can only block — which is the actual mitigation for the blacklisting risk,
  more than any particular rate value.
- Honouring `Retry-After` means that when the source does tell us what it wants, we obey it
  instead of guessing, which is both more effective and more defensible than our own curve.
- One policy every future source adapter inherits, and "by construction" means it: with the
  raw client unreachable from adapter packages and a build rule saying so, shipping an
  adapter that bypasses the pacing is not a mistake someone can quietly make.
- The breaker can see the failure that matters. Because an unusable response counts against
  it without being retried, a source that blocks us behind a `200` eventually opens the
  circuit instead of being absorbed silently as a content error.
- The domain stays free of transport concerns. Resilience4j's vocabulary is contained within
  the decorator and never reaches a port, so adopting, tuning or one day replacing the
  library is an `infrastructure` change that no domain code can observe.
- Declaring retryability at the adapter rather than by method keeps `POST`-based searches
  retryable without opening the door to double-submitting a state-changing request.
- Every value is configuration, so tuning against the source's observed tolerance is a
  deployment change rather than a release.

### Cons
- A **third-party dependency outside the Micronaut platform BOM** — a version to track and
  upgrade in `libs.versions.toml`, where almost everything else is resolved for us.
- We are using Resilience4j **in a way its own Micronaut documentation does not describe**,
  because that documentation targets a framework version we are past. The programmatic API
  is the library's stable core so the risk is low, but we forgo the annotation ergonomics
  and inherit no upstream guidance for this combination.
- **The circuit breaker is close to inert until more request kinds exist.** A run making a
  single request will never reach a meaningful `minimumNumberOfCalls`, and the open-state
  duration is meaningless across runs a day apart. It is configured now so the policy need
  not be reopened once runs fan out — but until then it is machinery that earns nothing.
- Retry being outermost means one logical failure records **several** failures in the
  breaker's window. That is the right accounting for an outbound client, where each attempt
  really was a failed call, but the thresholds only make sense when read with that in mind.
- Blocking on a permit **occupies a virtual thread** — cheap under
  [ADR-0011](0011-blocking-io-virtual-threads.md), but not free, and a maximum wait set too
  generously converts a fast, visible failure into a slow, silent one.
- Another layer between adapter and transport is **another thing to reason about** when
  diagnosing a failed run, and it invalidates the existing unit tests that stub
  `HttpClient.toBlocking()` directly.
- The initial rate, backoff and breaker settings are **educated guesses**. The source
  documents no limits, so the only way to learn the right values is to run against it and
  watch — which means the first configuration is provisional by construction.
- **A sequential default buys politeness with wall-clock time.** One request in flight at a
  deliberately slow rate makes any fanned-out run a long-running job by construction — hours,
  not minutes, at thousands of requests. It also means the rate is shared across every kind of
  request: a run walking result pages and a run fetching detail documents compete for the same
  budget rather than each getting one. That interacts with the scheduler and with any
  job-level timeout, and it is the direct price of the guarantee above.
- **Rate, concurrency and maximum wait are coupled**, and the coupling is not obvious from
  reading any one of them. A configuration violating the inequality does not fail at startup;
  it fails as refused permits under load, at whatever hour the scheduler runs. Until
  something validates the three together, review is the only thing enforcing it.
- **Errors are translated twice** before anything reaches a use case. That is what keeps the
  domain isolated, but it also means a failure's original cause is two hops from where it is
  read, and each hop is a chance to lose detail that would have helped diagnose a bad run.
- **What is genuinely reusable is the policy and the decorator, not the wiring.** Each source
  still declares its own configuration record and its own factory; nothing here produces a
  client per configured source automatically. Micronaut's `@EachProperty`/`@EachBean` would,
  but it is unverified against records on this framework version and would replace the
  `@Named` qualifier pattern the codebase uses today — a pattern this project has already
  been bitten by. Committing the decision to an unproven mechanism was the worse risk, so
  "reusable across sources" should be read as *the policy is written once*, not *a new source
  is free*.
- **No ceiling on a whole logical call**, only on its parts. The worst-case formula above is
  minutes for a single request, and nothing enforces it — a badly tuned set of knobs produces
  a slow run rather than a rejected configuration.
- **Adapters now carry a judgement with weight.** An acceptability check that is too strict
  opens the circuit against a source that is merely returning something unusual, and one that
  is too lax leaves the breaker as blind as it was. That judgement sits in each adapter,
  where the payload knowledge is, which is also where it is easiest to get subtly wrong.
- Retryability being an adapter-declared property rather than a mechanical rule puts a
  correctness burden on each adapter author: a `POST` wrongly declared idempotent would be
  retried, and nothing but review catches that.
