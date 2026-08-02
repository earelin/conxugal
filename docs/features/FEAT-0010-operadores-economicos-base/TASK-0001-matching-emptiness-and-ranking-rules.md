---
feat: FEAT-0010
domain: backend
adrs: [0002]
status: todo
depends_on: []
---

# Matching, emptiness and ranking rules

The three pure functions the operador catalogue rests on: what makes two awards name the same
operador (R3), what makes an identifier unusable (R5), and which contract supplies the spelling
an operador is displayed under (R4). Domain only — no store, no framework, no entity yet.
Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md).

These are decided first because everything else is downstream of them: the store is unique on
the match key, and the aggregate carries the rank. Getting them wrong is not a bug that shows up
as an error — SPEC-0006 is blunt that a mismatch "fails silently, and a quiet undercount is worse
than an error".

## Scope
- **The match key** — the published fiscal identifier reduced by ignoring **surrounding
  whitespace and letter case, and nothing else**. Internal spacing, punctuation and any differing
  character produce a different key, so two real suppliers are never merged into one.
- **The emptiness test** — an identifier absent, or empty once surrounding whitespace is ignored,
  is **unusable**: it yields no match key at all. Nothing beyond emptiness is validated; the
  source publishes irregular but genuine identifiers, and rejecting them would discard real
  awards.
- **The rank** — a comparable triple over a contract: its interpreted publication date (**null
  ranks last**), then its publication identifier (**higher wins**). This is what R4 means by
  *most recently published*, made total and deterministic rather than "some tie-break".
- The match key is a **comparison value that is never displayed** (R13). Name it so that reaching
  for it where a published spelling belongs reads as wrong.
- No I/O, no bean, no annotation: these are functions over values, and they are what the rest of
  the feature is tested against.

## Acceptance criteria
- Identifiers differing **only** in surrounding whitespace or letter case reduce to the **same**
  match key — ` B12345678 `, `b12345678` and `B12345678` are one.
  ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #3, matching half)
- Identifiers differing in **internal spacing, punctuation, or any character** reduce to
  **different** keys. Over-merging is asserted as its own case, not inferred from the merge case
  passing. (SPEC-0006 #4)
- An absent identifier, an empty one and a whitespace-only one are all **unusable**; an
  irregular but non-empty one — a foreign VAT number, a malformed NIF — is **usable** and
  reduces like any other. (SPEC-0006 #9)
- The rank orders a dated contract above an undated one however late the undated one arrives,
  and breaks a date tie by the higher publication identifier; comparing a contract with itself
  is not a win, so re-running a comparison never flips a decision. (SPEC-0006 #7)
- Unit-tested on values alone — no database, no HTTP, no Micronaut context.
