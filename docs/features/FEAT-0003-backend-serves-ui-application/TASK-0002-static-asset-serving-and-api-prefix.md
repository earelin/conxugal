---
feat: FEAT-0003
adrs: [0003, 0004, 0005, 0006]
status: done
depends_on: [TASK-0001]
---

# Static asset serving at `/` and the reserved `/api/` prefix

Governed by [ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md) (static-asset
delivery), [ADR-0006](../../architecture/0006-reserved-api-url-prefix.md) (reserved
`/api/` prefix), and [ADR-0005](../../architecture/0005-session-based-authentication.md)
("the SPA bundle is served only once a session exists") for the newly-introduced `/`
route. Application-module driving-side config only.

## Scope
- Micronaut `micronaut.router.static-resources` (or equivalent) mapping the UI assets
  copied in TASK-0001 to the origin root (`/`), with correct content types.
- Gate that new `/` route (and the assets under it) behind the existing session
  authentication, per ADR-0005 — an `intercept-url-map` entry, not a new authorization
  *decision*: before this task nothing was served at `/`, so no access rule existed for
  it; ADR-0005 already settled the policy, this task is its first application to actual
  served content. Endpoint-level `USER`/`ADMIN` authorization inside the SPA/API stays
  out of scope (FEAT-0002).
- Reserve `/api/` as the path prefix every REST endpoint must use from now on; document
  the convention in `server/CLAUDE.md` so future endpoint work follows it without
  re-deciding.
- No endpoints exist yet under `/api/` — this task only reserves and documents the
  prefix so TASK-0003's fallback can rely on it.

## Acceptance criteria
- Requesting a known built asset (e.g. `/assets/index-<hash>.js`) with a valid session
  returns it with a 200 and the correct `Content-Type`.
- Requesting `/` or a known built asset **without** a session does not serve the SPA
  (ADR-0005) — the existing unauthenticated redirect/401 behavior applies, same as any
  other protected route.
- Requesting an undefined path under `/api/` returns a plain 404 (no static-resource or
  fallback handling swallows it), both with and without a session.
- `server/CLAUDE.md` documents the `/api/` convention.
