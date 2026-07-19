---
feat: FEAT-0003
adrs: [0003, 0004, 0006, 0007]
status: done
depends_on: [TASK-0003]
---

# Vite base path and end-to-end integration test

Governed by [ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md). Closes the
"asset base path" edge case `docs/features/FEAT-0001-ui-application-scaffolding.md`
deferred here.

## Scope
- Confirm (or set) Vite's `base` in `ui/vite.config.ts` for root-path serving (`/`), to
  match TASK-0002's static-resource mapping — no sub-path is currently planned, so this
  is expected to stay the Vite default, but must be verified once the real build is
  served by Micronaut rather than `vite preview`.
- One acceptance test (`acceptance` module) exercising the full
  routing matrix with authentication built across this feature's tasks end to end, against the real built
  UI assets — not mocked.
- Use playwright for a real browser tests

## Acceptance criteria
- Booting the packaged server (built via TASK-0001) and requesting `/` returns HTML
  whose asset references (`<script>`/`<link>` tags) resolve to assets the server
  actually serves (no broken/misresolved paths).
- An acceptance test asserts: login of the demo user `/` → 200 HTML; `/acerca` → 200 HTML (fallback);
  `/rota-que-non-existe` → 200 HTML (fallback); `/api/rota-que-non-existe` → 404; a real
  static asset path → 200 with the asset body.
