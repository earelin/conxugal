---
feat: FEAT-0006
domain: backend
adrs: [0002, 0011, 0014]
status: done
depends_on: []
---

# Resilient advice for declarative outbound clients

The resilience policy every source's declarative `@Client` interface runs under: retry, rate
limiting and circuit breaking applied by our own around advice, per
[ADR-0014](../../architecture/0014-resilient-throttled-outbound-http-client.md). Blocking I/O
on the virtual-thread executor per
[ADR-0011](../../architecture/0011-blocking-io-virtual-threads.md); it lives on the driven side
of [ADR-0002](../../architecture/0002-hexagonal-architecture.md)'s hexagon. Limited to the
configuration, the policy beans and the interceptor — **no adapter changes** and no ArchUnit
rule, both of which land in TASK-0008.

## Scope
- Add `resilience4j-bom` to the version catalogue with `resilience4j-retry`,
  `resilience4j-ratelimiter` and `resilience4j-circuitbreaker` as version-free entries, and
  declare them in `infrastructure`.
- A `@ConfigurationProperties` record binding the contratosdegalicia **policy** settings: rate
  refresh interval, maximum permit wait, concurrency bound, retry attempts and backoff, the
  `Retry-After` clamp, and the breaker's failure-rate threshold and open-state duration.
  Transport settings — base URL, connect and read timeouts — are **not** in this record; they
  bind to the source's `micronaut.http.services` entry, which `@Client(id = ...)` resolves.
- The record validates itself as it binds, rejecting a combination Resilience4j would only reject
  later, from inside the first outbound call.
- A `@Factory` turning that record into the source's `Retry`, `RateLimiter` and `CircuitBreaker`
  beans — one of each, since two rate limiters would each hold a full budget and silently double
  the rate the source sees.
- A `@ResilientClient` annotation meta-annotated `@Around`, and the `MethodInterceptor` bound to
  it, composing the three policies longhand around the intercepted call —
  `Retry(CircuitBreaker(RateLimiter(context.proceed(this))))` — not with the `Decorators` builder,
  which lives in `resilience4j-all` and applies policies inside-out. The re-entrant
  `proceed(Interceptor)` form is required: the chain advances an index as it runs, so a plain
  `proceed()` cannot be called a second time and a retry would fail with
  `UnimplementedAdviceException` instead of reaching the client.
- Rate limiter with `limitForPeriod` held at 1 and the pace set by the refresh interval;
  concurrency bound defaulting to 1. Defaults must satisfy
  `maxConcurrent × limitRefreshPeriod / limitForPeriod ≤ maxWait`.
- Circuit breaker configured for **failure-rate detection only** — no slow-call detection, whose
  threshold would otherwise be tripped by the permit wait the breaker encloses.
- Retry restricted to an explicit, closed set of transient failures (connection failures,
  resets, read timeouts, and `408`, `425`, `429`, `500`, `502`, `503`, `504`); to methods
  declared `@Get` or `@Head`; and to any other method only where the declaration says
  `@ResilientClient(idempotent = true)`.
- `Retry-After` honoured on `429`/`503` in both RFC 9110 forms — `delay-seconds` and HTTP-date —
  read from the `HttpClientResponseException` the generated client throws, clamped to the
  configured maximum, aborting rather than waiting beyond it. Absent the header, exponential
  backoff with jitter.
- An identifying `User-Agent` naming the project and a contact URL, declared as a `@Header`
  stereotype on the advice annotation so every client carrying the policy sends it, with its
  value published as a property by an `ApplicationContextConfigurer`.
- Translate `RequestNotPermitted` and `CallNotPermittedException` into the transport exception
  type adapters already catch, and exclude both from retry, so no Resilience4j type escapes the
  interceptor.
- Conservative defaults in configuration: at most one request in flight per source and no faster
  than one request per second.

## Acceptance criteria
- A transient failure followed by a success returns the successful response, and the number of
  attempts matches the configured limit; a permanent status (e.g. `403`) is not retried.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #13)
- `Retry-After` is honoured in both `delay-seconds` and HTTP-date form, and a value above the
  clamp aborts the call instead of waiting it out.
- A sequence of requests is paced no faster than the configured rate, and the pacing holds
  across retried attempts as well as first attempts.
- A refused permit and an open circuit surface as the transport exception type — no Resilience4j
  type escapes the interceptor, and neither condition is retried.
- A `@Post` method without `idempotent = true` is never retried; one with it is.
- Every outbound request carries the identifying `User-Agent`.
- **The advice is demonstrably applied** to a declarative `@Client` interface annotated
  `@ResilientClient` — a test that fails if the interceptor stops running, since advice silently
  not applied is this design's quiet failure mode.
- An invalid policy configuration (e.g. a permit wait below the refresh interval × concurrency)
  is rejected when the configuration binds, naming the offending setting, rather than surfacing
  later as refused permits under load.
- Behaviour is integration-tested against a **stubbed** source (WireMock scenarios for the
  retry, `Retry-After` and pacing cases) — no live network.
