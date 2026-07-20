---
feat: FEAT-0005
domain: backend
adrs: [0002]
status: done
depends_on: []
---

# Runtime-metrics domain: sample model + source port

Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md) (hexagonal). Domain
only — no transport, no adapter, no storage. Adds the value type that every later task carries
and the port the stream pulls from.

## Scope
- `RuntimeMetrics` immutable value in `server/domain`: sample timestamp, JVM figures (heap and
  non-heap used, heap max, live thread count, uptime, recent GC activity), system/CPU load,
  HTTP request and error counters, and datastore-pool usage (active / idle / max). Field names
  and units mirror the `RuntimeMetrics` schema of the
  [OpenAPI contract](../../api/openapi.yaml).
- `RuntimeMetricsSource` port with a single operation returning the **current** sample.
- No repository, no entity, no persistence-mapping annotation: nothing here is stored.

## Acceptance criteria
- `RuntimeMetrics` is immutable and carries no credential, connection string, or URL field —
  the type makes a secret unrepresentable rather than relying on the adapter to omit it.
  ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #18)
- `RuntimeMetricsSource` exposes only a "current sample" operation: there is no query for a
  past sample and no accumulation behind the port. (SPEC-0003 #17)
- The domain module compiles with no dependency on the infrastructure or application modules,
  as enforced by the existing architecture tests. (ADR-0002)
- Unit-tested without an application context, database, or HTTP server.
