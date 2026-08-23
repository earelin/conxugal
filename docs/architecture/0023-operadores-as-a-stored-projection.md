---
status: accepted
date: 2026-08-01
spec: SPEC-0006
supersedes: null
superseded_by: null
---

# 0023. Operadores económicos are a stored projection, maintained by the import that feeds it

## Status
Accepted

Recorded as `0018` when it was written, and renumbered to `0023` to resolve a collision with
[ADR-0018](0018-frontend-acceptance-tests-against-a-stubbed-api.md), which took that number first.
The decision, its date and its consequences are unchanged; only the number is. Anything citing
`ADR-0018` for the operadores projection — including the pull request that introduced this record —
means this one.

## Context
[SPEC-0006](../specs/SPEC-0006-operadores-economicos.md) turns the awardee named on every
contract into a catalogue of **operadores económicos**, and states that the catalogue is
**derived, never imported** — an operador exists because a contract names it and for no other
reason (R2). It then leaves exactly one decision open and says it is ADR-grade:

> **Whether the catalogue is stored state or computed on read.** … That choice decides whether
> R7's lifecycle happens automatically or has to be driven, and whether R14's reads are viable
> at all over hundreds of thousands of operadores.

Five facts constrain the answer.

**Identity is unambiguous, and the source always supplies it.** Every contract published for an
Órgano names its awardee together with a **NIF/CIF**, and that identifier — not the name, not a
similarity score — is what identifies an operador (R3). So the catalogue never has to guess: two
awards name the same operador exactly when their identifiers are equal under R3's whitespace-
and case-insensitive comparison, and an operador's identity is settled the moment its first
contract is stored and never revised as more arrive. This is what makes a **stored** projection
cheap to keep correct: there is no merge of two rows later discovered to be one, no split of one
later discovered to be two, and no confidence threshold to tune. A catalogue built on name
similarity could not be stored this way — it would need every new contract to be able to
retro-actively re-partition rows already written.

**The volume is asymmetric.** R14 expects hundreds of thousands of operadores over millions of
contracts, and the reads it names are not point lookups: the operadores list spans the whole
catalogue, its **last page is one click away** (R11), and **name lookup is a partial, case- and
accent-insensitive match** over every operador. R14 says outright that name lookup "is the
single most expensive read this spec defines and the one whose result most directly decides the
open stored-versus-computed question", and that deferring a latency budget is a bigger bet here
than in SPEC-0005, because nothing bounds a selection the way one Órgano in one year bounds an
Órgano's contracts.

**Every read needs a per-operador top-1.** R4 displays an operador under the name from its
**most recently published** contract, ties broken by the higher contract identifier, with undated
contracts ranked last. R8 then **orders the whole list by that name** and matches fragments of
it. Computed on read, one page of the operadores list is
a top-1-per-group over millions of contract rows, ordered and filtered by the result of that
top-1 — before paging into it positionally.

**Writes are rare, bounded and already batched.** The catalogue changes only when contracts change,
and contracts change only during an import: an initial import, an incremental window, or a
correction refreshed in place.
[FEAT-0009](../features/FEAT-0009-contratos-menores-initial-import/README.md) already commits
contracts in batches and already runs one import at a time system-wide
([SPEC-0005](../specs/SPEC-0005-import-browse-contratos-menores.md) R22), so there is exactly one
writer, and it is a writer that is already paying for a batch commit.

**The derivation is not monotonic.** R7 requires the catalogue to "re-derive from the contracts
as they currently stand": a correction can move a contract to a different operador, a withdrawal
can leave an operador with no visible contracts, and a restoration can bring one back. So a
projection cannot be an append-only accumulation — it has to be maintained against changes that
subtract, not only add.

## Decision
The catalogue is **stored state**: an `operador_economico` row per distinct fiscal identifier under
R3's equivalence, maintained by the import that stores the contracts it derives from, with each
contract carrying a **foreign key to its operador**.

> **Amended.** R3 has since been widened to admit one party the source declines to identify — a
> UTE, catalogued per bid and holding no fiscal identifier — so the row cardinality is *per
> distinct fiscal identifier, plus one row per bid by an unidentified UTE*. The identifier column
> is nullable for that case alone. Everything else in this record stands, and the reasoning below
> is unaffected: an identifier-less row is never *matched* on anything, so it can neither absorb
> another party's contract nor be re-partitioned once written.

**The identity is the identifier itself, held canonical.** The row carries **one** fiscal
identifier column in R3's canonical form — trimmed of surrounding whitespace, upper-cased — under
a unique constraint, so "two spellings are one operador" is true at the store level and not only
in use-case logic. It is both what the row is matched on and what is displayed: there is no second
representation, because R3 and R13 accept the canonical form as the identifier this system holds.
The published letter case is retained nowhere.

**The import resolves and writes the link.** When a contract batch is stored, each contract's
published identifier is canonicalised; an empty one yields **no operador** (R5) and
leaves the contract's foreign key null; otherwise the operador is found or created and the
contract row is written pointing at it. This happens **inside the batch's transaction**, so a
contract and its link commit together and a crash cannot leave a stored contract whose operador
was never created.

**The link stays nullable even though the identifier is always published.** The two specs
disagree here — SPEC-0005 lists the fiscal identifier among what the source *does* publish,
while SPEC-0006 R5 defines what happens when one is absent — and this ADR sides with neither by
making the empty case impossible. A `NOT NULL` foreign key would turn a single malformed
publication into a failed batch in a job measured in days, and
[SPEC-0005](../specs/SPEC-0005-import-browse-contratos-menores.md) R27 is explicit that a value
the system cannot use is not a reason to reject the contract carrying it. The branch is
therefore expected never to be taken in production and is kept anyway, at the cost of one
nullable column.

**The operador keeps a surrogate UUID** alongside its unique fiscal identifier, as
`OrganoDeContratacion` keeps one alongside its `sourceKey`. Keying rows directly on the
identifier would save a lookup on the import's hot path, at the price of putting a published
value in every foreign key — the thing that precedent exists to avoid.

**R4's name is maintained on the row, not computed.** A stored contract carries the
rank R4 defines — its interpreted publication date, undated ranking last, and its contract
identifier as the tie-break — and the import advances the operador's name when it stores a
contract that outranks the incumbent. The comparison is against the row, not against a scan of
the operador's contracts, so the cost is per contract stored rather than per operador read.
R4 ranks the name alone: the identifier is canonical and identical from every contract, so
there is nothing about it to rank.

**Being derived is expressed as a rule about writers, not as a computation.** No surface creates,
renames or deletes an operador (SPEC-0006 R1, #29); the only writer is the derivation. That is
what keeps R2 true — an operador exists because a contract names it — without the catalogue
having to be recomputed to prove it.

**What this decision does not settle**, because no requirement yet forces it and the cheapest
answer depends on measurements R14 has not taken:

- **How R7's lifecycle and R4's demotion are driven when a change subtracts.** Withdrawal (
  SPEC-0005 R13) and corrections that move a contract between operadores are not built by the
  first operadores feature, and neither is R7's "unreachable when no visible contract remains".
  Maintaining a name forward is a comparison; maintaining it backward — when the winning
  contract is withdrawn or corrected out — needs either a recomputation for that one operador or
  a visible-contract count on the row. Both are local to one operador and both remain open.
  SPEC-0006 R15 supplies the **data** a backward fix needs, by retaining every name an operador
  has borne; it does not perform the fix, and nothing here does. The
  obligation this ADR does accept is that whatever drives them writes to **this** row rather
  than introducing a second, computed notion of an operador.
- **Whether reachability is a stored count or a query.** R7 makes an operador reachable exactly
  while it has a visible contract; with a foreign key that is answerable either way.

## Consequences

### Pros
- R14's two hardest reads — the operadores list ordered by name, and partial name lookup
  — become an index over one table of hundreds of thousands of rows, instead of a
  top-1-per-group over millions of contracts computed before the first row can be ordered.
- Because identity is the published NIF/CIF rather than a similarity judgement, a row written
  today is never wrong tomorrow about *which* operador it is. The projection can go stale only in
  what it **displays** (below), never in how it **partitions** — which is the failure a stored
  catalogue would otherwise be exposed to and a computed one would not.
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
- **The projection can be wrong**, which a computed catalogue cannot be. A name is
  correct only while the contract that won R4 still wins it; a correction or withdrawal that
  demotes the winner leaves the row stale until something recomputes it, and the first
  operadores feature does not build that something. This is the real price, and it is why the
  paragraph above names it as unsettled rather than silently deferring it.

  **Two things narrow this cost without removing it.** The **identifier half no longer exists**:
  R3 holds one canonical identifier reached from every contract identically, so there is no
  published spelling that could go stale. And for the **name**, SPEC-0006 R15 retains every name
  an operador has been published under, each with the rank it was last seen at, so demoting a
  stale one becomes a choice among rows already stored rather than a re-read of every contract.
  **The demotion itself is still not performed** — R7's lifecycle owns it, and the open question
  above stands. What R15 buys is that the fix, when built, needs no backfill only a re-import
  could supply.
- **The import gets slower and more coupled.** Storing a contrato menor now also reads and
  possibly writes an operador row, so the batch commit grows and the hot path of a multi-day job
  acquires a second table. The contention is bounded by R22's one-import-at-a-time guard, but it
  is real.
- **A second unique-key contest.** Concurrent creation of the same operador is impossible today
  because there is one importer, so the create-if-absent path rests on a guarantee outside this
  ADR; if a second writer ever appears — a second family importing in parallel, a backfill — the
  insert must handle the conflict rather than assume it away.
- **The published letter case of an identifier is not retained**, which is a real narrowing of
  R13 and is recorded there rather than here. It buys away a con this record carried in draft —
  two representations of one identifier on every row, where a reader picking the wrong one
  breached R13 in the display or R3 in the matching — at the cost of displaying a form no
  contract necessarily published. Case is the one difference R3 rules meaningless for identity,
  which is what makes the trade acceptable.
- **Reversing this costs a migration**, not a rewrite of a query: the table and the foreign key
  would have to be dropped from a `contrato_menor` table holding millions of rows.
- The decision is taken **before** R14's measurements exist, on a cost argument rather than an
  observation. If name lookup turns out to be cheap computed, this ADR bought little; if it
  turns out to be expensive stored, the answer is an index, not a different home.
