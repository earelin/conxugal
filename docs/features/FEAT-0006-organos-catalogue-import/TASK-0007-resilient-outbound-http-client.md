---
feat: FEAT-0006
domain: backend
adrs: [0002, 0011, 0014]
status: todo
depends_on: []
---

# Resilient, self-throttling outbound HTTP client

The shared outbound HTTP client every source adapter will call through: retry, rate limiting
and circuit breaking over the Micronaut `BlockingHttpClient`, per
[ADR-0014](../../architecture/0014-resilient-throttled-outbound-http-client.md). Blocking I/O
on the virtual-thread executor per
[ADR-0011](../../architecture/0011-blocking-io-virtual-threads.md); it lives on the driven
side of [ADR-0002](../../architecture/0002-hexagonal-architecture.md)'s hexagon. Limited to
building the client and its configuration — **no adapter changes** and no ArchUnit rule, both
of which land in TASK-0008.

## Scope
- Add `resilience4j-bom` to the version catalogue with `resilience4j-retry`,
  `resilience4j-ratelimiter` and `resilience4j-circuitbreaker` as version-free entries, and
  declare them in `infrastructure`.
- A shared outbound-HTTP package in `infrastructure` holding the decorator and a per-source
  `@ConfigurationProperties` record: base URL, connect and read timeouts, rate refresh
  interval, maximum permit wait, concurrency bound, retry attempts and backoff, the
  `Retry-After` clamp, and the breaker's failure-rate threshold and open-state duration.
- Compose the three policies longhand — `Retry(CircuitBreaker(RateLimiter(exchange)))` — not
  with the `Decorators` builder, which lives in `resilience4j-all` and applies policies
  inside-out.
- Rate limiter with `limitForPeriod` held at 1 and the pace set by the refresh interval;
  concurrency bound defaulting to 1. Defaults must satisfy
  `maxConcurrent × limitRefreshPeriod / limitForPeriod ≤ maxWait`.
- Circuit breaker configured for **failure-rate detection only** — no slow-call detection,
  whose threshold would otherwise be tripped by the permit wait the breaker encloses.
- Retry restricted to an explicit, closed set of transient failures (connection failures,
  resets, read timeouts, and `408`, `425`, `429`, `500`, `502`, `503`, `504`), to safe methods
  by default, and to non-safe methods only where the caller declares the request idempotent.
- `Retry-After` honoured on `429`/`503` in both RFC 9110 forms — `delay-seconds` and
  HTTP-date — clamped to the configured maximum, aborting rather than waiting beyond it.
  Absent the header, exponential backoff with jitter.
- An identifying `User-Agent` naming the project and a contact URL, set once in the decorator.
- Translate `RequestNotPermitted` and `CallNotPermittedException` into the transport exception
  type adapters already catch, and exclude both from retry, so no Resilience4j type escapes
  the decorator.
- Accept a caller-supplied acceptability check and record a rejected response as a breaker
  failure **without** retrying it. Design point for the implementer: the check must be able to
  reject on parsed content without forcing callers to parse twice — e.g. the call takes the
  caller's response mapper together with a predicate over its result.
- Conservative defaults in configuration: at most one request in flight per source and no
  faster than one request per second.

## Acceptance criteria
- A transient failure followed by a success returns the successful response, and the number of
  attempts matches the configured limit; a permanent status (e.g. `404`) is not retried.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #13)
- `Retry-After` is honoured in both `delay-seconds` and HTTP-date form, and a value above the
  clamp aborts the call instead of waiting it out.
- A sequence of requests is paced no faster than the configured rate, and the pacing holds
  across retried attempts as well as first attempts.
- A refused permit and an open circuit surface as the transport exception type — no
  Resilience4j type escapes the decorator, and neither condition is retried.
- A response rejected by the acceptability check is recorded as a breaker failure and is not
  retried.
- A non-safe request without an explicit idempotency declaration is never retried; one with it
  is.
- Every outbound request carries the identifying `User-Agent`.
- Behaviour is integration-tested against a **stubbed** source (WireMock scenarios for the
  retry, `Retry-After` and pacing cases) — no live network.
