---
status: accepted
date: 2026-07-11
spec: SPEC-0003
supersedes: null
superseded_by: null
---

# 0009. Stream admin real-time metrics over Server-Sent Events

## Status
Accepted

## Context
[SPEC-0003](../specs/SPEC-0003-administration-area.md) gives administrators a system
dashboard. Its basic operational status — overall service state and datastore
reachability — is a coarse snapshot served by a request/response endpoint
(`GET /api/admin/system-status`) that stays as is.

Beyond that snapshot we want to expose **detailed runtime metrics** (JVM memory, threads,
GC, uptime, request/error counters, datastore-pool usage, and similar) to administrators
**live**, so they can watch a running instance while debugging. These metrics are
transient: they are **not** persisted in the backend and carry no historical query
requirement. A viewer wants the current values as they change, not a stored series.

A polled snapshot fits this poorly — to look "live" it forces the client to hammer the
endpoint, and the cadence is a client guess rather than the server pushing when a value
changes. The realistic transports for server→client push are Server-Sent Events (SSE) and
WebSockets. The data flow here is strictly one-directional (server → admin browser) and
text/JSON, so a WebSocket's bidirectional, sub-protocol machinery is unneeded weight.

Authentication is session-cookie based
([ADR-0005](0005-session-based-authentication.md)). A browser `EventSource` sends the
session cookie with its request, so the same `@Secured("ADMIN")` gate that protects the
rest of `/api/admin/**` applies to a streaming endpoint unchanged — no bearer-token or
custom-header handling is needed on the long-lived connection.

This is a cross-cutting choice: it introduces a streaming transport and a server-push
pattern to the stack, not a detail of one endpoint. It is therefore recorded as a
decision. The detailed-metrics feature is its first consumer.

## Decision
Expose admin real-time detailed metrics over **Server-Sent Events** (`text/event-stream`),
under the reserved `/api/` prefix ([ADR-0006](0006-reserved-api-url-prefix.md)) and gated
by `@Secured("ADMIN")`.

- The stream is **one-directional** (server → client) and secured by the existing session
  cookie; a non-`ADMIN` or unauthenticated request is denied like any other `/api/admin/**`
  call.
- Metrics are **computed on demand and streamed**; the backend **never stores** them —
  there is no server-side history or retention. A reconnecting client starts from the
  current values.
- The stream carries a periodic **heartbeat** so a dropped connection is detected and the
  browser's native `EventSource` reconnection can re-establish it.
- The basic `GET /api/admin/system-status` snapshot is unaffected and remains the source of
  coarse status; SSE is added only for the detailed live metrics.

## Consequences

### Pros
- Administrators see metrics update live without the client polling; the server pushes on
  its own cadence.
- SSE runs over plain HTTP and `EventSource` is a browser primitive with built-in
  auto-reconnect, so the client side is small and needs no extra library.
- The session cookie authenticates the stream, so the ADR-0005 auth model and the
  `@Secured("ADMIN")` gate are reused verbatim — no new credential path on a long-lived
  connection.
- Not storing metrics keeps the backend stateless for this concern: no schema, migration,
  retention, or data-protection surface.

### Cons
- Each open dashboard holds a long-lived server connection/thread for its lifetime; this is
  bounded (administrators are few) but is real and must be capped and closed cleanly.
- SSE is one-directional and text-only; any future need for client→server streaming or
  binary frames would not be served by this decision and would need its own record.
- Long-lived streaming can be broken by intermediary buffering/timeouts (reverse proxies);
  response buffering must be disabled for the stream and the heartbeat relied on to keep it
  alive.
- There is no server-side replay: a client that reconnects loses whatever it missed while
  disconnected. Acceptable because the metrics are live debugging aids, not an audit record.
