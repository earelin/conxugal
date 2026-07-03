---
feat: FEAT-0003
adrs: [0003, 0004, 0006]
status: todo
depends_on: [TASK-0002]
---

# SPA history-fallback for unmatched non-API GET requests

Governed by [ADR-0003](../../architecture/0003-react-router-ui-served-by-backend.md)
(backend-owned SPA fallback). Closes the production gap
`docs/features/FEAT-0001-ui-application-scaffolding.md` flagged as a dependency on this
feature.

## Scope
- A low-priority route/error handler: any **GET** request that does not start with
  `/api/` and does not match a static asset served by TASK-0002 returns `index.html`
  with a 200 status, so the client-side router takes over.
- Applies uniformly to both known SPA routes (e.g. `/acerca`) and unknown ones — React
  Router's own `NotFoundPage` (FEAT-0001) renders the in-shell Galician not-found state
  either way; the backend does not need a client route table.
- Non-GET requests (POST/PUT/etc.) to unmatched non-`/api/` paths are unaffected by this
  fallback — those still 404/405 normally (no SPA form ever posts outside `/api/`).

## Acceptance criteria ([SPEC-0001](../../specs/SPEC-0001-web-ui.md))
- GET `/acerca` (a real client-side route) returns `index.html`, 200 (SPEC-0001 AC2 —
  "reload returns to the same section" — now holds in production).
- GET `/rota-que-non-existe` (no matching client-side route either) also returns
  `index.html`, 200; the SPA then shows its own not-found page (SPEC-0001 AC4).
- GET `/api/rota-que-non-existe` returns a plain 404, never `index.html`.
- GET of a known static asset path is served as the asset, not the fallback.
