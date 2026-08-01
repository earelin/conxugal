---
feat: FEAT-0006
domain: backend
adrs: [0002, 0014]
status: todo
depends_on: [TASK-0003, TASK-0007]
---

# Adopt the declarative resilient client in the source adapter

Move the Órganos source adapter onto a declarative `@Client` interface carrying TASK-0007's
advice, and make the policy unbypassable, per
[ADR-0014](../../architecture/0014-resilient-throttled-outbound-http-client.md). This is the
half of the ADR that changes existing behaviour; it is separated from TASK-0007 because the
ArchUnit rule below cannot pass while the adapter and its factory still name `HttpClient`.

## Scope
- Declare a `@Client(id = "contratosdegalicia")` interface for the portada fetch, annotated
  `@ResilientClient`, returning the raw body bytes. The adapter injects that interface and no
  longer builds a request, holds a client, or names a Micronaut HTTP client type; the existing
  `@Factory` that constructs the client is deleted.
- Bind the source's transport configuration under `micronaut.http.services.contratosdegalicia`
  — keeping the existing base URL and read timeout and adding the connect timeout, which is
  unset today and inherits OS behaviour — replacing the current flat `@ConfigurationProperties`
  record.
- Keep the adapter's "implausibly small list" judgement exactly where it is, as the port's typed
  domain failure. Per ADR-0014 it does **not** feed the circuit breaker: content judgement sits
  outside the advice, so a source answering `200` with a block page fails the run without
  opening the circuit.
- An ArchUnit rule in the `architecture` module, in the same idiom as
  [ADR-0006](../../architecture/0006-reserved-api-url-prefix.md)'s URL-prefix rule: no
  `infrastructure` class may depend on `HttpClient` or `BlockingHttpClient`. Scoped to main
  sources, so tests may still drive clients directly.
- Update the existing adapter unit tests, which stub `HttpClient.toBlocking()` directly and will
  no longer exercise the transport path.

## Acceptance criteria
- The adapter names no Micronaut HTTP client type, and every outbound request it makes goes
  through a declarative client carrying the advice.
- `./gradlew :architecture:test` fails if any `infrastructure` class depends on `HttpClient` or
  `BlockingHttpClient`.
- An unreachable source, or an unusable, empty or implausibly small response, still surfaces as
  the port's typed failure — unchanged from TASK-0003.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #13)
- A transient failure during a fetch is retried and the import succeeds, verified against a
  stubbed source — no live network.
- The existing integration tests for parsing, ISO-8859-1 decoding and failure handling still
  pass unchanged. (SPEC-0004 #3)
