---
feat: FEAT-0005
domain: frontend
adrs: [0009, 0018]
status: done
depends_on: [TASK-0005]
---

# Frontend acceptance tests for the metrics panel

Governed by [ADR-0018](../../architecture/0018-frontend-acceptance-tests-against-a-stubbed-api.md) and [ADR-0009](../../architecture/0009-sse-admin-realtime-metrics.md). Black-box Playwright coverage of the metrics panel delivered by [TASK-0005](TASK-0005-metrics-ui-panel.md), driving the built SPA with the `GET /api/admin/metrics` stream served by WireMock. [FEAT-0004 TASK-0008](../FEAT-0004-administration-area/TASK-0008-frontend-acceptance-tests.md) built the harness and stubbed the stream, but left it unasserted; this closes that gap.

## Scope
- An `acceptance/specs/admin-metrics.spec.ts` covering the journeys an administrator actually has with the panel: samples arriving on their own, the client-side history and its loss on reload, a dropped stream reconnecting, and secrets never reaching the screen.
- A `stubEventStream` helper that makes a WireMock stream deterministic: event frames padded to a common width so each lands in exactly one dribbled chunk, a leading `retry:` field that pins the browser's reconnect delay, and trailing heartbeat comments that hold the connection open past the last sample as the real endpoint's heartbeat does.
- Request-journal helpers (`requestCountFor`, `clearRequestJournal`) so a stream that was never opened, or never closed, is observable from outside the browser.
- `role="group"` and an `aria-labelledby` on each metric tile and detail card, matching the dashboard's status cards, so a figure rendered in two places can be asserted per card rather than counted page-wide.

**Out of scope:** the sample cadence, the heartbeat interval and the server's `@Secured("ADMIN")` gate — WireMock replays a canned body, so none of them can be proven here; they belong to the backend suite ([ADR-0007](../../architecture/0007-acceptance-testing-module.md)). Also out of scope: the panel's error and empty-field paths, which the component tests already cover, and any change to the default cadence of `ui/wiremock/mappings/metrics.json`.

## Acceptance criteria
- An administrator opening the dashboard sees the metric tiles and detail cards fill from the stream and then change again, with no click, reload or refresh control in between. ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #15)
- A non-administrator gets no metrics panel and the SPA opens no stream for them; the real refusal stays the server's. (SPEC-0003 #16)
- The sample counters show the history growing in the browser, and after a reload the panel starts again from one sample — nothing is carried across. (SPEC-0003 #17)
- A sample carrying a connection string or password alongside the pool counts renders neither: the panel shows the fields it knows and both privacy notes. (SPEC-0003 #18)
- When the stream ends, the panel keeps the last sample on screen captioned as stale, reports itself reconnecting, and resumes on its own with the history intact. ([ADR-0009](../../architecture/0009-sse-admin-realtime-metrics.md))
- Leaving the dashboard closes the stream: no further connection is opened once the panel is gone. (ADR-0009)
- Journeys are driven only through the rendered page — accessible roles, labels and the Galician copy of `strings.ts` — never through application internals, and each scenario is reproducible in isolation.
- No assertion depends on a locale-formatted date, whose rendering differs between browser builds.
- `npm run lint`, `npm run test`, `npm run build` and `npm run test:acceptance` stay green.
