---
status: accepted
date: 2026-08-25
spec: SPEC-0003
supersedes: null
superseded_by: null
---

# 0025. Read runtime metrics straight from the platform, with no metrics library

## Status
Accepted

## Context
[ADR-0009](0009-sse-admin-realtime-metrics.md) settled *how* detailed runtime metrics reach an
administrator — a Server-Sent Events stream — but not *where the figures come from*.
[SPEC-0003](../specs/SPEC-0003-administration-area.md) R17 names the sample set: memory,
threads, uptime, and request and datastore-pool counters. R20 forbids storing any of it.

The reflex answer to "expose JVM metrics" is a metrics library. Micronaut has first-class
Micrometer support (`micronaut-micrometer-core` plus per-registry modules), and adding it would
bring JVM, system, HTTP-server and Hikari binders that between them already cover every figure
R17 asks for, with almost no code of our own.

Three things make that a worse fit than it first looks:

- The sample set is **small, fixed and closed**. R20 rules out history, so there is nothing to
  aggregate, no window to configure, and no time series to query — the one operation is "read
  the current values". A registry is machinery for a problem this feature does not have.
- Micrometer is **cross-cutting by design**. Its value comes from a registry every component
  publishes into and an exporter shipping to a backend; adopting it for one admin panel means
  either carrying that surface unused, or letting it grow a monitoring stack we have not
  specified and no requirement asks for.
- The figures are **already on the platform**. `java.lang.management` exposes memory, threads,
  uptime and GC through the standard MXBeans, and Hikari publishes pool usage through its own
  `HikariPoolMXBean`. Micrometer's JVM and Hikari binders read exactly these sources; going
  through a registry to reach them adds an indirection, not a capability.

The one figure the platform does *not* offer is HTTP request and error totals: the instance
keeps none of its own.

## Decision
Assemble each `RuntimeMetrics` sample by reading the running instance directly, with no metrics
library on the classpath.

- **JVM and system figures** come from the platform MXBeans via `ManagementFactory` — memory,
  threads, uptime, garbage-collection count and time, and CPU load where the platform reports
  one.
- **Datastore-pool usage** comes from Hikari's own `HikariPoolMXBean` gauges — active and idle
  connections and the configured maximum, and nothing else the pool knows.
- **HTTP totals** come from a small in-house counter (`HttpRequestCounter`, two `LongAdder`s)
  fed by a server filter, rather than from a framework meter.
- A figure a platform does not report **degrades to an absent value** rather than failing the
  whole sample.

Adopting Micrometer — or any metrics library — later is a decision in its own right and needs
its own ADR.

## Consequences

### Pros
+ No dependency, registry, exporter or configuration surface is added for a panel that shows
  fourteen fields; the reading code is one adapter class.
+ Nothing is carried that R20 forbids using — there is no aggregation or retention machinery
  sitting unused behind the port.
+ The adapter reads the same sources a Micrometer binder would, so replacing it later is a
  swap behind `RuntimeMetricsSource`, not a rewrite of its callers.
+ The HTTP counter costs two atomic increments per request and holds no path, header or
  payload, so it cannot become a route by which a secret reaches a sample.

### Cons
− The pool reader is **bound to Hikari specifically**: `RuntimeMetricsAdapter` unwraps the
  `DataSource` and fails fast if it is not a `HikariDataSource`. Changing pool implementation
  means changing this adapter.
− The HTTP totals are **process-lifetime and in-memory**: they reset on restart, are per
  instance, and a second replica would report its own. Acceptable while the deployment is a
  single instance; a second one makes these figures ambiguous.
− Every new metric is hand-written, including the portability handling a binder would give for
  free — the `-1` sentinel the JDK documents for an undefined `MemoryUsage.getMax()` or GC
  count has to be folded explicitly.
− There is no `/metrics` scrape endpoint, so an external monitoring system cannot collect from
  this instance. Nothing asks for one today; adding it would be Micrometer's ADR to make.

<!-- distilled-from: FEAT-0005 @ 525bdaa -->
