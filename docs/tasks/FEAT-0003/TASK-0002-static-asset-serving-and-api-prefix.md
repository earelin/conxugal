---
feat: FEAT-0003
adrs: [0003, 0004]
status: todo
depends_on: [TASK-0001]
---

# Static asset serving at `/` and the reserved `/api/` prefix

Governed by [ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md) (static-asset
delivery). Application-module driving-side config only.

## Scope
- Micronaut `micronaut.router.static-resources` (or equivalent) mapping the UI assets
  copied in TASK-0001 to the origin root (`/`), with correct content types.
- Reserve `/api/` as the path prefix every REST endpoint must use from now on; document
  the convention in `server/CLAUDE.md` so future endpoint work follows it without
  re-deciding.
- No endpoints exist yet under `/api/` — this task only reserves and documents the
  prefix so TASK-0003's fallback can rely on it.

## Acceptance criteria
- Requesting a known built asset (e.g. `/assets/index-<hash>.js`) returns it with a
  200 and the correct `Content-Type`.
- Requesting an undefined path under `/api/` returns a plain 404 (no static-resource or
  fallback handling swallows it).
- `server/CLAUDE.md` documents the `/api/` convention.
