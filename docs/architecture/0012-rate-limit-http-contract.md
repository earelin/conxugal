---
status: proposed
date: 2026-07-18
spec: null
supersedes: null
superseded_by: null
---

# 0012. Declare rate limiting in the HTTP contract with 429 and RateLimit headers

## Status
Proposed

## Context
Adopting the OWASP `recommended` ruleset in the vacuum linter
([`.vacuum.yaml`](../../.vacuum.yaml)) flagged the API contract for having no defence
against clients issuing requests faster than the service should accept. OWASP treats an
un-throttled API as a security weakness (unrestricted resource consumption), and the
linter therefore expects every operation to advertise rate-limit metadata and a
`429 Too Many Requests` response.

The API's HTTP surface is a hand-authored, authoritative OpenAPI document
([ADR-0010](0010-design-first-openapi-contract.md)); controllers conform to it and CI
validates the running API against it. So the shape of a rate-limit response is a
contract-level decision, not a per-controller detail. It is also cross-cutting: every
current and future endpoint under the reserved `/api/` prefix
([ADR-0006](0006-reserved-api-url-prefix.md), which already names rate limiting as a
future concern targeting all endpoints at once) is bound by whatever convention we pick,
including the Server-Sent Events metrics stream
([ADR-0009](0009-sse-admin-realtime-metrics.md)). That makes it a decision to record
rather than a choice to repeat.

There is a naming choice to settle. The IETF `RateLimit` header fields draft and RFC 9110
(`Retry-After`) define several accepted spellings; the linter accepts any of them. We need
one convention so the contract, the DTOs, and the eventual enforcement layer agree.

A caveat worth stating plainly: no component enforces a rate limit today. This decision
governs the *contract* the platform commits to. Whatever ends up doing the throttling — a
reverse proxy in front of the origin, or a filter in the `application` module — must emit
these headers and status for the contract test to keep passing once enforcement lands.

## Decision
Declare rate limiting as part of the HTTP contract for **every** `/api/**` operation.

- Each operation advertises the current window on **every** response — success and error
  alike — via three response headers: **`RateLimit-Limit`**, **`RateLimit-Remaining`** and
  **`RateLimit-Reset`** (seconds until the window resets). These are defined once as
  reusable `components.headers` and referenced from each response.
- Each operation declares a **`429 Too Many Requests`** response for a caller that exceeds
  the allowed rate. Its body is the shared `Error` schema and it additionally carries a
  **`Retry-After`** header (seconds to wait), alongside the three `RateLimit-*` headers.
- The `429` response is defined once as a reusable `components.responses` entry
  (`TooManyRequests`) and referenced from each operation.
- Enforcement is intentionally **out of scope for this ADR**: the decision fixes the
  contract, not where or how the limit is applied. When enforcement is built it must
  conform to this contract, and a feature/spec will define the concrete limits.

## Consequences

### Pros
- The contract honestly advertises that the API is rate limited, so clients (including the
  SPA and generated client types) can read `RateLimit-*`, back off on `429`, and respect
  `Retry-After` without guessing.
- One reusable header set and one reusable `429` response keep the convention consistent
  across every endpoint and cheap to apply to new ones — no per-endpoint decision.
- The design-first document and its CI contract test stay green against the OWASP ruleset,
  so the security expectation is enforced automatically rather than by review.
- Picking a single `RateLimit-*` / `Retry-After` spelling now avoids divergent header
  naming across endpoints later.

### Cons
- The contract is **ahead of the implementation**: until an enforcement layer emits these
  headers and the `429`, the document promises behaviour the running service does not yet
  provide. The contract test only checks declared responses, so this gap is not caught
  automatically.
- Every response gains three headers and every operation a `429`, enlarging the document
  and the Java DTO/response surface that must mirror it ([ADR-0010](0010-design-first-openapi-contract.md)'s
  two-places cost).
- Committing to `RateLimit-*` names ties us to that convention; changing it later is a
  breaking contract change needing a new ADR.
- Choosing concrete limits, the enforcement mechanism, and per-role or per-endpoint
  budgets is deferred; those still need a spec/feature before the contract is truthful.
