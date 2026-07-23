---
feat: FEAT-0005
domain: backend
adrs: [0002]
status: done
depends_on: [TASK-0001, TASK-0002]
---

# Runtime-metrics source adapter

Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md) (hexagonal). The
driven adapter in `server/infrastructure` that implements `RuntimeMetricsSource` by reading the
running instance directly — `java.lang.management` MXBeans, the datastore pool's own gauges, and
[TASK-0002](TASK-0002-http-request-error-counters.md)'s counters. No metrics library is
introduced; adopting one would be a cross-cutting decision needing its own ADR.

## Scope
- Read JVM figures from the platform MXBeans: heap and non-heap usage, live thread count,
  uptime, and recent GC collection count/time.
- Read system/CPU load from the operating-system MXBean, tolerating the platforms where it is
  unavailable (report the value as absent rather than failing the sample).
- Read datastore-pool usage (active, idle, max) from the connection pool's own gauges — usage
  figures only, never the pool's configuration, JDBC URL, user, or password.
- Assemble one `RuntimeMetrics` per call, timestamped from the injected clock. No caching, no
  accumulation, no field held between calls.

## Acceptance criteria
- Each call returns a freshly assembled sample: two calls separated by allocation and a request
  report different figures, and nothing is served from a cache.
  ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #15, #17)
- The adapter holds no history and there is no operation returning a previous sample.
  (SPEC-0003 #17)
- An assembled sample, serialised to JSON, contains no credential, connection string, host,
  token, or key — asserted explicitly against the configured datastore password and URL.
  (SPEC-0003 #18)
- A metric whose source is unavailable on the running platform degrades to an absent value and
  never fails the whole sample.
- Integration-tested against a real datastore pool for the pool gauges; unit-tested for the JVM
  and counter mapping.
