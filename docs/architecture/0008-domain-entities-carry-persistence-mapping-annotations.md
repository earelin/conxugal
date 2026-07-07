---
status: accepted
date: 2026-07-07
spec: null
supersedes: null
superseded_by: null
---

# 0008. Domain entities carry their own persistence-mapping annotations

## Status
Accepted

## Context
[ADR-0002](0002-hexagonal-architecture.md) keeps the `domain` module "free of transport
and persistence concerns," allowing only Micronaut dependency-injection annotations on
domain classes; anything persistence-related was expected to live on a separate
`infrastructure`-only type.

[TASK-0002](../features/FEAT-0002-user-authentication/TASK-0002-auth-infrastructure-postgres-user-store.md)
(FEAT-0002) followed that rule literally: persisting the domain `User` record (email,
password hash, role) via Micronaut Data JDBC required a parallel infrastructure-only
`UserEntity` (carrying `@MappedEntity`/`@Id`), plus a `JdbcUserRepository` adapter whose
only job was copying fields between `UserEntity` and `User`. The two types are — and are
expected to stay — structurally identical; the mapping code exists solely to satisfy the
persistence-purity rule, not because the persisted shape differs from the domain shape.

This project favors a simple, data-driven approach: most aggregates (contracts, tenders,
and now users) map directly onto a single table with no divergence between the persisted
row and the domain model. Requiring a shadow entity and a hand-written mapper for every
such case doubles the class count and gives the team two copies of the same fields to
keep in sync, for a flexibility (persisted shape diverging from domain shape) that isn't
needed today and, if it ever is, can be introduced exactly where it's needed instead of
paid for everywhere up front.

## Decision
Domain classes **may** carry Micronaut Data persistence-mapping annotations
(`@MappedEntity`, `@Id`, `@GeneratedValue`, `@MappedProperty`, etc.) directly, for a
simple, direct 1:1 mapping to a single table or collection — instead of always
introducing a separate infrastructure-only mapped entity and hand-written mapping code.

This narrows the persistence-purity clause of [ADR-0002](0002-hexagonal-architecture.md)
for domain classes doing simple, direct mapping. Everything else ADR-0002 decided is
unchanged: the three-module split, the dependency direction (`application` and
`infrastructure` depend only on `domain`; `domain` depends on nothing), and the
DTO-avoidance principle — which now also covers persistence entities: introduce a
separate infrastructure-only entity (and mapping) only where it earns its place, e.g.:

- the persisted shape genuinely diverges from the domain shape (denormalization, a
  legacy/external schema, a composite or multi-table aggregate);
- a domain concept has no natural single-table/collection persistence at all.

The repository **port** (e.g. `UserRepository`) still lives in `domain`; only its
Micronaut Data-backed implementation (the generated `@JdbcRepository`/equivalent) lives
in `infrastructure`. Domain gains persistence *annotations* as metadata — not
persistence *code*: JDBC/SQL, connections, dialects, and transactions stay entirely in
`infrastructure`.

## Consequences
+ Fewer classes for the common case: one domain type serves as both the business model
  and the persisted row, removing the hand-written mapping code TASK-0002 needed for
  `UserEntity` ↔ `User`.
+ Matches the project's preference for a simple, data-driven approach over layering
  ceremony that isn't paying for itself yet.
+ The repository port stays in `domain`, so the domain's public contract (what callers
  depend on) is unaffected; only the concrete persisted type changes shape.
− `domain` now takes a compile-time dependency on Micronaut Data's annotation/model
  types, which is a persistence-framework dependency — a partial reversal of ADR-0002's
  "free of persistence concerns" isolation. Domain unit tests stay framework-free at
  *runtime* (the annotations are metadata, not behavior), but the module's dependency
  surface grows.
− If a persisted shape ever needs to diverge from its domain type, the annotations must
  be removed from that domain class and a proper infrastructure-only entity introduced
  in its place — this ADR defers that cost until it's actually needed, it doesn't
  remove it.
− Slightly blurs "the domain is independent of storage" from ADR-0002: the type a
  repository port returns is now visibly a database-mapped type, even though `domain`
  still has no dependency on a specific driver, connection, or SQL dialect.
