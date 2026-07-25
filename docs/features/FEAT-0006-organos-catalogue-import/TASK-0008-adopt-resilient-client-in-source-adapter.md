---
feat: FEAT-0006
domain: backend
adrs: [0002, 0014]
status: todo
depends_on: [TASK-0003, TASK-0007]
---

# Adopt the resilient client in the source adapter

Move the Órganos source adapter onto the resilient client from TASK-0007 and make the policy
unbypassable, per
[ADR-0014](../../architecture/0014-resilient-throttled-outbound-http-client.md). This is the
half of the ADR that changes existing behaviour; it is separated from TASK-0007 because the
ArchUnit rule below cannot pass while the adapter and its factory still name `HttpClient`.

## Scope
- Move raw client construction out of the Órganos package into the shared outbound-HTTP
  package, so the adapter receives the decorated client and no longer builds or names a
  Micronaut `HttpClient`.
- Fold the adapter's existing "implausibly small list" judgement into the acceptability check
  it hands to the client, so a source that answers `200` with a block or challenge page counts
  against the circuit breaker instead of being absorbed as a plain content error. The failure
  the port surfaces is unchanged.
- Bind the per-source configuration for contratosdegalicia — replacing the current flat record
  — keeping the existing base URL and read timeout and adding the connect timeout, which is
  unset today and inherits OS behaviour.
- An ArchUnit rule in the `architecture` module, in the same idiom as
  [ADR-0006](../../architecture/0006-reserved-api-url-prefix.md)'s URL-prefix rule: no
  `infrastructure` class outside the shared outbound-HTTP package may depend on
  `HttpClient` or `BlockingHttpClient`. Scoped to main sources, so tests may still drive
  clients directly.
- Update the existing adapter unit tests, which stub `HttpClient.toBlocking()` directly and
  will no longer exercise the transport path.

## Acceptance criteria
- The adapter names no Micronaut HTTP client type, and every outbound request it makes goes
  through the resilient client.
- `./gradlew :architecture:test` fails if any `infrastructure` class outside the shared
  outbound-HTTP package depends on `HttpClient` or `BlockingHttpClient`.
- An unreachable source, or an unusable, empty or implausibly small response, still surfaces
  as the port's typed failure — unchanged from TASK-0003.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #13)
- An implausibly small response is recorded as a circuit-breaker failure and is not retried.
- A transient failure during a fetch is retried and the import succeeds, verified against a
  stubbed source — no live network.
- The existing integration tests for parsing, ISO-8859-1 decoding and failure handling still
  pass unchanged. (SPEC-0004 #3)
