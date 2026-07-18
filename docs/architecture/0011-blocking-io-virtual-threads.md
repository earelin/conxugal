---
status: accepted
date: 2026-07-15
spec: null
supersedes: null
superseded_by: null
---

# 0011. Run HTTP request handling on blocking I/O over virtual threads, not the Netty event loop

## Status
Accepted

## Context
[ADR-0001](0001-backend-stack.md) picked Micronaut on Java 25 but left the server's
threading model unspecified. Micronaut's default (`ThreadSelection.MANUAL`) is Netty's
reactive event loop: a small, fixed pool of threads that must never block, with
blocking work explicitly redirected to a separate executor per call site (`@ExecuteOn`,
`@Blocking` under `AUTO` selection only, or an
`HttpRequestExecutorAuthenticationProvider`-style dispatch).

Most of this application's work is blocking by nature: `micronaut-data-jdbc` over a
JDBC driver, Argon2id password verification, and outbound HTTP scraping/ingestion
against contratosdegalicia.gal. Relying on per-call-site dispatch to keep this off the
event loop is easy to get wrong silently — exactly what surfaced while scoping
[TASK-0008](../features/FEAT-0002-user-authentication/TASK-0008-run-controllers-on-blocking-virtual-threads.md):
`UserAuthenticationProvider` was already annotated `@Blocking`, but that annotation is a
no-op under Micronaut's default (`MANUAL`) thread selection, so every login would have
blocked an event-loop thread absent a per-provider fix.

Java 25 (ADR-0001) includes virtual threads (JDK 21+, stable, no preview flag).
Micronaut's `ThreadSelection` model (`io.micronaut.scheduling.executor.ThreadSelection`)
offers a `BLOCKING` mode: every operation — regardless of return type or annotations,
and as of Micronaut 5 this also covers filters and request event listeners, not just
route execution — runs on the configured `blocking` executor and never on the server
event loop. Combined with `virtual: true` on that executor, this gives blocking-style
code server-wide without reasoning about the event loop per call site, at the cost of
the event loop's raw non-blocking throughput ceiling.

## Decision
The server sets `micronaut.server.thread-selection: BLOCKING` and
`micronaut.executors.blocking.virtual: true`. Every request — controllers, filters,
security providers — runs on the `blocking` executor backed by virtual threads,
never on a Netty event-loop thread. This is a single, global configuration change:
no per-`@Controller`/`@Filter` annotation is required, and any endpoint added later
inherits it automatically.

## Consequences

### Pros
- Closes the class of bug that per-call-site dispatch leaves open by construction:
  blocking code is safe everywhere, with nothing to opt into per endpoint and nothing
  for a reviewer to check on new code.
- Application code stays plain and imperative — no reactive types needed to keep the
  event loop unblocked.
- Virtual threads scale cheaply to many concurrently blocked requests (JDBC waits,
  password hashing, outbound scraping calls) without provisioning a large platform
  thread pool.

### Cons
- Gives up the event loop's non-blocking throughput ceiling; not the right tradeoff if
  the workload becomes read-heavy at very high QPS with mostly non-blocking I/O —
  revisit with a new ADR if that materializes.
- Virtual threads pin their carrier thread during `synchronized` blocks or native
  (JNI) calls; the JDBC driver and the Argon2id encoder ([TASK-0003](../features/FEAT-0002-user-authentication/TASK-0003-security-config-session-auth.md))
  need verifying under load to confirm neither pins pathologically.
- A narrower, per-provider dispatch (`HttpRequestExecutorAuthenticationProvider`) is no
  longer needed once the whole request path defaults to the blocking executor —
  [TASK-0008](../features/FEAT-0002-user-authentication/TASK-0008-run-controllers-on-blocking-virtual-threads.md)
  keeps the simpler `HttpRequestAuthenticationProvider` instead.
