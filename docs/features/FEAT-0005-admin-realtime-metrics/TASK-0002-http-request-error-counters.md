---
feat: FEAT-0005
domain: backend
adrs: [0002, 0006]
status: done
depends_on: [TASK-0001]
---

# HTTP request/error counters

The instance keeps no HTTP counters of its own, so the figures `RuntimeMetrics` reports have to
come from somewhere. Adds a lightweight in-memory counter fed by a server filter, so
[TASK-0003](TASK-0003-runtime-metrics-source-adapter.md) can read request and error totals
without pulling in a metrics framework.

## Scope
- A counter component holding two monotonic totals (requests served, responses with a `4xx`/`5xx`
  status or a thrown error), incremented with non-blocking atomics.
- An HTTP server filter that increments them for every request, including requests to the
  metrics stream itself.
- Counters are process-lifetime totals held in memory only: no store, no window, no reset
  endpoint.

## Acceptance criteria
- After N requests of which M failed, the counter reports N requests and M errors.
  ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #15)
- The counters record only totals — no path, query string, header, or payload is retained, so
  no secret can reach a sample. (SPEC-0003 #18)
- Counter values live only in memory and are lost on restart; nothing is persisted and no
  endpoint returns a past value. (SPEC-0003 #17)
- The filter adds no measurable per-request blocking work and does not alter any response
  status or body — verified by the existing endpoint tests still passing unchanged.
- Unit-tested for concurrent increments; integration-tested that a real request moves the
  counters.
