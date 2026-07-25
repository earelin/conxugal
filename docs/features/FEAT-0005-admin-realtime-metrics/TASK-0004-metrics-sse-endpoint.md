---
feat: FEAT-0005
domain: backend
adrs: [0002, 0005, 0006, 0009, 0010]
status: done
depends_on: [TASK-0003]
---

# Metrics SSE endpoint

Governed by [ADR-0009](../../architecture/0009-sse-admin-realtime-metrics.md) (SSE transport),
[ADR-0006](../../architecture/0006-reserved-api-url-prefix.md) (reserved `/api/` prefix),
[ADR-0005](../../architecture/0005-session-based-authentication.md) (`@Secured`, session cookie)
and [ADR-0010](../../architecture/0010-design-first-openapi-contract.md) (design-first
contract). Adds the driving side only; the sample itself comes from
[TASK-0003](TASK-0003-runtime-metrics-source-adapter.md).

## Scope
- `GET /api/admin/metrics`, `@Secured("ADMIN")`, producing `text/event-stream` and conforming to
  the `streamMetrics` operation and `RuntimeMetrics` schema of the
  [OpenAPI contract](../../api/openapi.yaml).
- Emit an initial sample immediately on subscribe, then a fresh sample on a fixed
  server-chosen interval (a configuration property with a sane default; the client cannot
  request a different cadence — the contract exposes no parameter for it).
- Interleave a periodic heartbeat comment so a dead connection is detected on both ends.
- Disable response buffering for the stream and cancel the subscription — releasing the
  connection and its timer — when the client disconnects.
- Each event's `data` is one `RuntimeMetrics` sample assembled on demand through the port; the
  endpoint keeps no buffer of what it has already sent.

## Acceptance criteria
- A `USER` or unauthenticated caller is denied exactly like any other `/api/admin/**` call; an
  `ADMIN` gets an open `text/event-stream`.
  ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #16)
- A subscribing `ADMIN` receives a first sample without waiting for the interval, then further
  samples on the interval, each reflecting the instance's state at the time it was emitted.
  (SPEC-0003 #15)
- Heartbeats arrive on the stream between samples. (ADR-0009)
- When the client disconnects, the server-side subscription and its timer are released — an
  open stream that is abandoned leaves no running task behind. (ADR-0009)
- A reconnecting client resumes from the current sample; the server replays nothing and exposes
  no past sample by any route. (SPEC-0003 #17)
- No secret or credential value appears in any emitted event. (SPEC-0003 #18)
- Integration-tested for the `ADMIN`/`USER` gate, the initial-plus-interval samples, and clean
  teardown on disconnect.
