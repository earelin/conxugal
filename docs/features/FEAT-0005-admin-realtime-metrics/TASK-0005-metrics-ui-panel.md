---
feat: FEAT-0005
domain: frontend
adrs: [0004, 0009]
status: done
depends_on: [TASK-0004]
---

# Metrics UI panel on the admin dashboard

Governed by [ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md) (Vite + Mantine SPA)
and [ADR-0009](../../architecture/0009-sse-admin-realtime-metrics.md). Adds a live metrics panel
to the existing admin dashboard, consuming
[TASK-0004](TASK-0004-metrics-sse-endpoint.md)'s stream.

The visual target is [`design/`](design/README.md).

## Scope
- A **Métricas** panel on the admin dashboard opening an `EventSource` to
  `/api/admin/metrics` (session cookie sent by the browser; no new credential handling).
- Render each arriving sample live: JVM memory, threads, uptime, GC, system load, HTTP request
  and error counters, and datastore-pool usage.
- Keep a bounded rolling history of the most recent samples **in component state only**, for
  recent-samples sparklines; no `localStorage`, `sessionStorage`, cookie, or server call
  persists it.
- Close the `EventSource` on unmount; rely on `EventSource`'s built-in reconnection when the
  stream drops, showing a discreet "reconectando" state meanwhile.
- Galician chrome and copy in `strings.ts`, consistent with the existing dashboard.

## Acceptance criteria
- An `ADMIN` on the dashboard sees the metric values change as samples arrive, with no manual
  refresh. ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #15)
- The panel is not shown to a `USER`; this hiding is cosmetic — the server gate remains the
  real one. (SPEC-0003 #16)
- The rolling history is capped at a fixed number of samples, and reloading or navigating away
  from the dashboard leaves nothing behind: after a reload the panel starts from the next
  sample with an empty history. (SPEC-0003 #17)
- The panel renders only what the stream returns and displays no secret value. (SPEC-0003 #18)
- Unmounting the panel closes the `EventSource`; a dropped stream reconnects without a page
  reload. (ADR-0009)
- All added chrome and messages are in Galician. (SPEC-0001 #6)
- Component-tested with a stubbed `EventSource` for live updates, the bounded history, and
  close-on-unmount.
