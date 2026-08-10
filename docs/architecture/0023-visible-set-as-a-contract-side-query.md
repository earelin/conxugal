---
status: proposed
date: 2026-08-10
spec: SPEC-0004
supersedes: null
superseded_by: null
---

# 0023. Derive the visible set by querying the contract side, not by marking the Órgano

## Status
Proposed

## Context

[SPEC-0004](../specs/SPEC-0004-import-manage-organos-contratacion.md) R9 scopes what a reader is
served from the catalogue to **the visible set**: every Órgano with at least one **visible
contract, in any family**. `GET /api/organos` returns that set and nothing else;
`GET /api/admin/organos` returns the whole catalogue for the administration area. SPEC-0004's
*Decisions left open* names the derivation as ADR-grade and declines to settle it, and
[SPEC-0005](../specs/SPEC-0005-import-browse-contratos-menores.md) R14 depends on the same set
from the other side.

The rule needs a fact about **contracts** to scope a read of **Órganos**, and under
[ADR-0002](0002-hexagonal-architecture.md) those are two areas of the domain —
`gal.conxugal.domain.organo` and `gal.conxugal.domain.contrato`, with `contrato_menor` already
carrying a foreign key to `organo_contratacion`. Three shapes were available:

1. **Query the contract side** when the catalogue is read — a semi-join from the catalogue to
   whichever contract tables exist.
2. **Maintain a marker on the Órgano** — a flag or a count the import advances, so the catalogue
   read stays a single-table scan.
3. **A read model spanning both**, maintained separately from either aggregate.

The choice is not local. It sets the pattern every later contract family inherits — licitacións
first — and it governs a read whose cost SPEC-0004 R20 makes measurable against
[SPEC-0005](../specs/SPEC-0005-import-browse-contratos-menores.md) R24's reference environment.
It is also the decision [FEAT-0011](../features/FEAT-0011-contratos-menores-browsing/README.md)
would otherwise take by building one.

Two facts constrain it more than the shapes themselves do.

**Visibility is not a property the import knows.** *Visible* rather than *stored* is deliberate:
SPEC-0005 R13 lets an administrator **remove** a contract, which keeps it stored while removing
it from every list, and R13's restore reverses that. An Órgano can therefore leave and re-enter
the visible set through an action that touches no import and imports no row. A marker maintained
by the import would be wrong precisely in the case the predicate was worded to catch — and
SPEC-0004 #21 requires both directions of that transition.

**The catalogue is small and the contracts are not.** The catalogue is a few hundred rows; the
question asked of the contract side is *does at least one visible row exist for this Órgano*,
which is a semi-join answered by an index, not an aggregate over millions.
[ADR-0018](0018-operadores-as-a-stored-projection.md) reached the opposite conclusion for
operadores, and the difference is the reason: that projection carries *derived attributes* — a
name chosen by rank, a canonical identifier — that cost a top-1-per-group over the whole contract
table on every read. This one carries a **boolean per Órgano over a few hundred**.

## Decision

**The catalogue read derives the visible set by querying the contract side. No column, count or
flag records it on `organo_contratacion`.**

The predicate is *there exists at least one visible contract, of any family, whose Órgano is
this one*, evaluated when the catalogue is read.

- **The contract side owns the predicate.** Each contract family exposes, through its own domain
  port, whether it holds visible contracts for a set of Órganos. The catalogue read composes the
  answers; it does not reach into any family's tables and does not know how any family defines
  *visible*. R13's removal is that family's business, expressed in its own query.
- **A new family joins by adding a port implementation**, not by editing the catalogue read's
  SQL and not by backfilling a marker. This is what keeps SPEC-0004 R9's *of any family* honest
  rather than aspirational: today only `contrato_menor` can satisfy the predicate.
- **The scoping is applied in the read that serves the catalogue**, so it cannot be forgotten by
  a caller. `GET /api/admin/organos` does not apply it; that is the whole difference between the
  two paths.
- **It is not an access control.** It scopes what a listing returns. It adds no permission check
  to reads of an individual Órgano's own data, and an Órgano's identity is not a secret — see
  SPEC-0004 R9 and SPEC-0005 R14, which both state this.
- **Cost is measured, not assumed.** SPEC-0004 R20 puts this read on SPEC-0005 R24's reference
  environment, alongside R24's own reads, and fixes no budget. If measurement shows the semi-join
  is inadequate at real volumes, the answer is a **new ADR adopting a maintained marker with an
  invalidation path for R13**, not a column added quietly under this one.

## Consequences

### Pros

- **It cannot go stale.** The set is computed from the rows that define it, so a removal (R13), a
  restore, an import, or a family arriving are all reflected at the next read with nothing to
  invalidate. SPEC-0004 #21 — an Órgano entering the set on its first visible contract and leaving
  on its last — holds by construction rather than by remembering to update a counter in four
  places.
- **The dependency runs the way the specs already do.** SPEC-0005 consumes SPEC-0004; here the
  contract side answers a question the catalogue asks, so no contract feature has to write to an
  Órgano row it does not own, and SPEC-0004 R5's rule that reconciliation leaves administrator
  state alone gains nothing new to protect.
- **Adding a family is additive**, which is the property SPEC-0005 R15 requires of the family
  split and this rule mirrors: a port implementation, no migration, no backfill of a marker over
  a table of millions.
- **No write amplification on the busiest path in the system.** An initial import of a large
  Órgano stores over a million contracts; a maintained marker would have to be advanced or
  reconsidered during that walk, on top of the batch writes ADR-0017 already governs.

### Cons

- **The catalogue read is no longer a single-table scan.** Every call joins the catalogue to each
  contract family. At today's volumes — a few hundred Órganos, a semi-join on an indexed foreign
  key — this is expected to be cheap, but *expected* is doing real work in that sentence, which is
  why R20 measures it rather than asserting it.
- **The cost grows with the number of families**, one semi-join each, on a read the browse surface
  makes on every visit.
- **The domain boundary is crossed on a read path**, which the alternatives avoided. The port keeps
  the crossing explicit and one-directional, but `organo` now has a query-time dependency on
  `contrato` that did not exist before.
- **It may not survive measurement.** This is the shape most likely to be replaced later, and
  replacing it means a marker, an invalidation path for R13, and a backfill — the work this
  decision defers rather than eliminates. Deferring it is deliberate: CLAUDE.md forbids optimising
  before measurement shows the straightforward implementation falls short, and SPEC-0005 R24
  applies the same discipline to the reads beside it.
- **Nothing lets an administrator see the set as stored data**, since it is not stored. Diagnosing
  *why is this Órgano not showing* means running the query, not reading a column.
