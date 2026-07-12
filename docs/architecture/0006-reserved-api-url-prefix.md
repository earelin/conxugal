---
status: accepted
date: 2026-07-03
spec: null
supersedes: null
superseded_by: null
---

# 0006. Reserve the `/api/` URL prefix for REST endpoints

## Status
Accepted

## Context
[ADR-0003](0003-react-router-ui-served-by-backend.md) makes the Micronaut server the
single origin for both the REST API and the built SPA, and
[ADR-0004](0004-ui-stack-vite-mantine.md) uses history routing, which requires the
backend to serve `index.html` as an SPA fallback for unmatched non-asset paths so
client-side routes deep-link and survive a reload.

That fallback creates an ambiguity: when a request matches no server route, the server
must decide whether it is a **missing API endpoint** (a genuine 404 the caller should
see) or a **client-side SPA route** (serve the app shell with 200). Without a stable,
reserved namespace for the API, the two are indistinguishable — a typo'd or removed API
path would return `200 index.html` instead of `404`, misleading API clients and tests.

This is not one feature's concern: every current and future REST endpoint, across any
spec, is bound by whatever convention resolves the ambiguity. It is a cross-cutting URL
contract, so it is recorded as a decision rather than left as prose in a single feature
or module readme. [FEAT-0003](../features/FEAT-0003-backend-serves-ui-application/README.md) is
the first consumer, reserving the prefix and implementing the fallback split.

## Decision
Reserve the **`/api/`** URL prefix for all REST endpoints served by the `application`
module.

- Every REST endpoint — current and future — is mounted under `/api/**`.
- The SPA history fallback applies **only** to unmatched `GET` requests **outside**
  `/api/**` that do not match a static asset; those return `index.html` (200).
- An unmatched path **under** `/api/**` returns a genuine `404` (or the appropriate
  4xx), never the SPA fallback.
- Static assets and the SPA shell continue to be served at the origin root (`/`); only
  the API is namespaced.

This is a documented, enforceable convention: any endpoint added outside `/api/` is a
defect. It is restated in `server/CLAUDE.md` for day-to-day visibility, but this ADR is
the governing record.

## Consequences

### Pros
- The fallback can unambiguously separate "no such API route" (real 404) from "an SPA
  client-side route" (serve the shell), so API clients and tests get correct status
  codes.
- A single, predictable namespace for the API simplifies routing, reverse proxies,
  logging and future concerns (e.g. API-wide `@Secured` rules or rate limiting) that
  want to target all endpoints at once.
- Endpoint authors have one unambiguous rule with no per-endpoint decision to make.

### Cons
- Every endpoint path carries the `/api/` segment; the convention must be upheld by
  review/discipline, as nothing in the framework forces it by default.
- Changing the prefix later is a breaking change to every client and would need a new
  ADR superseding this one.
