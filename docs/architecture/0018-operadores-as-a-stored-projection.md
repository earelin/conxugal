---
status: proposed
date: 2026-08-01
spec: SPEC-0006
supersedes: null
superseded_by: null
---

# 0018. Operadores económicos are a stored projection, maintained by the import that feeds it

## Status
Proposed

## Context
[SPEC-0006](../specs/SPEC-0006-operadores-economicos.md) turns the awardee named on every
contract into a catalogue of **operadores económicos**, and states that the catalogue is
**derived, never imported** — an operador exists because a contract names it and for no other
reason (R2). It then leaves exactly one decision open and says it is ADR-grade:

> **Whether the catalogue is stored state or computed on read.** … That choice decides whether
> R7's lifecycle happens automatically or has to be driven, and whether R14's reads are viable
> at all over hundreds of thousands of operadores.

Four facts constrain the answer.

**The volume is asymmetric.** R14 expects hundreds of thousands of operadores over millions of
contracts, and the reads it names are not point lookups: the operadores list spans the whole
catalogue, its **last page is one click away** (R11), and **name lookup is a partial, case- and
accent-insensitive match** over every operador. R14 says outright that name lookup "is the
single most expensive read this spec defines and the one whose result most directly decides the
open stored-versus-computed question", and that deferring a latency budget is a bigger bet here
than in SPEC-0005, because nothing bounds a selection the way one Órgano in one year bounds an
Órgano's contracts.

**Every read needs a per-operador top-1.** R4 displays an operador under the name *and* the
identifier spelling from its **most recently published** contract, ties broken by the higher
contract identifier, with undated contracts ranked last. R8 then **orders the whole list by that
display name** and matches fragments of it. Computed on read, one page of the operadores list is
a top-1-per-group over millions of contract rows, ordered and filtered by the result of that
top-1 — before paging into it positionally.

**Writes are rare, bounded and already batched.** The catalogue changes only when contracts
change, and contracts change only during an import: an initial import, an incremental window, or
a correction refreshed in place. [FEAT-0009](../features/FEAT-0009-contratos-menores-initial-import/README.md)
already commits contracts in batches and already runs one import at a time system-wide
([SPEC-0005](../specs/SPEC-0005-import-browse-contratos-menores.md) R22), so there is exactly
one writer, and it is a writer that is already paying for a batch commit.

**The derivation is not monotonic.** R7 requires the catalogue to "re-derive from the contracts
as they currently stand": a correction can move a contract to a different operador, a withdrawal
can leave an operador with no visible contracts, and a restoration can bring one back. So a
projection cannot be an append-only accumulation — it has to be maintained against changes that
subtract, not only add.

## Decision
The catalogue is **stored state**: an `operador` row per distinct fiscal identifier under R3's
equivalence, maintained by the import that stores the contracts it derives from, with each
contract carrying a **foreign key to its operador**.

**The identity is a match key, and it is not displayed.** The row is keyed by the identifier
under R3's equivalence — trimmed of surrounding whitespace, case-folded — held as a column with
a unique constraint, so "two spellings are one operador" is true at the store level and not only
in use-case logic. R13 forbids displaying it: what is shown is the published spelling carried by
R4's winning contract, which the row holds as a separate value.

**The import resolves and writes the link.** When a contract batch is stored, each contract's
published identifier is reduced to the match key; an empty key yields **no operador** (R5) and
leaves the contract's foreign key null; otherwise the operador is found or created and the
contract row is written pointing at it. This happens **inside the batch's transaction**, so a
contract and its link commit together and a crash cannot leave a stored contract whose operador
was never created.

**R4's display fields are maintained on the row, not computed.** A stored contract carries the
rank R4 defines — its interpreted publication date, undated ranking last, and its contract
identifier as the tie-break — and the import advances the operador's display name and displayed
spelling when it stores a contract that outranks the incumbent. The comparison is against the
row, not against a scan of the operador's contracts, so the cost is per contract stored rather
than per operador read.

**Being derived is expressed as a rule about writers, not as a computation.** No surface creates,
renames or deletes an operador (SPEC-0006 R1, #29); the only writer is the derivation. That is
what keeps R2 true — an operador exists because a contract names it — without the catalogue
having to be recomputed to prove it.

**What this decision does not settle**, because no requirement yet forces it and the cheapest
answer depends on measurements R14 has not taken:

- **How R7's lifecycle and R4's demotion are driven when a change subtracts.** Withdrawal (
  SPEC-0005 R13) and corrections that move a contract between operadores are not built by the
  first operadores feature, and neither is R7's "unreachable when no visible contract remains".
  Maintaining a display name forward is a comparison; maintaining it backward — when the winning
  contract is withdrawn or corrected out — needs either a recomputation for that one operador or
  a visible-contract count on the row. Both are local to one operador and both remain open. The
  obligation this ADR does accept is that whatever drives them writes to **this** row rather
  than introducing a second, computed notion of an operador.
- **Whether reachability is a stored count or a query.** R7 makes an operador reachable exactly
  while it has a visible contract; with a foreign key that is answerable either way.

## Consequences

### Pros
- R14's two hardest reads — the operadores list ordered by display name, and partial name lookup
  — become an index over one table of hundreds of thousands of rows, instead of a
  top-1-per-group over millions of contracts computed before the first row can be ordered.
- R3's equivalence is enforced by a unique constraint, so the silent split the spec warns about
  ("matched naively the aggregation fails silently, and a quiet undercount is worse than an
  error") cannot arise from a use case forgetting to normalise.
- The cost lands on the writer, which is the side that can absorb it: one import at a time,
  already batching, already paced at ADR-0014's rate against the source, and already the
  bottleneck by orders of magnitude. Resolving an operador is arithmetic next to fetching the
  page the contract came from.
- A contract's link commits with the contract, so there is no window in which a stored contract
  has no operador and no reconciliation job to make one.
- Every later question — R7's lifecycle, R9's history, R14's measurements — is asked of one
  table that exists, rather than of a query shape that has to be invented first.

### Cons
- **The projection can be wrong**, which a computed catalogue cannot be. A display name is
  correct only while the contract that won R4 still wins it; a correction or withdrawal that
  demotes the winner leaves the row stale until something recomputes it, and the first
  operadores feature does not build that something. This is the real price, and it is why the
  paragraph above names it as unsettled rather than silently deferring it.
- **The import gets slower and more coupled.** Storing a contrato menor now also reads and
  possibly writes an operador row, so the batch commit grows and the hot path of a multi-day job
  acquires a second table. The contention is bounded by R22's one-import-at-a-time guard, but it
  is real.
- **A second unique-key contest.** Concurrent creation of the same operador is impossible today
  because there is one importer, so the create-if-absent path rests on a guarantee outside this
  ADR; if a second writer ever appears — a second family importing in parallel, a backfill — the
  insert must handle the conflict rather than assume it away.
- **Two representations of an identifier live on the row** — the match key and the published
  spelling — and a reader who picks the wrong one violates R13 in the display or R3 in the
  matching. The naming has to make that hard to get wrong.
- **Reversing this costs a migration**, not a rewrite of a query: the table and the foreign key
  would have to be dropped from a `contrato_menor` table holding millions of rows.
- The decision is taken **before** R14's measurements exist, on a cost argument rather than an
  observation. If name lookup turns out to be cheap computed, this ADR bought little; if it
  turns out to be expensive stored, the answer is an index, not a different home.
