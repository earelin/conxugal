---
feat: FEAT-0010
domain: backend
adrs: [0002]
status: todo
depends_on: []
---

# Canonicalisation, emptiness and ranking rules

The three pure functions the operador catalogue rests on: what makes two awards name the same
operador (R3), what makes an identifier unusable (R5), and which contract supplies the name
an operador is displayed under (R4). Domain only — no store, no framework, no entity yet.
Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md).

These are decided first because everything else is downstream of them: the store is unique on the
canonical fiscal identifier, and the aggregate carries the rank. Getting them wrong is not a bug
that shows up as an error — SPEC-0006 is blunt that a mismatch "fails silently, and a quiet
undercount is worse than an error".

## Scope
- **Canonicalising a fiscal identifier** — trim surrounding whitespace, upper-case the letters,
  **and nothing else**. Internal spacing, punctuation and any differing character survive, so two
  real suppliers are never merged into one. The result is **the identifier the system holds**: it
  is what the store is unique on, what a lookup compares against, and what is displayed. There is
  no second value beside it.
  - **This is the one value the system canonicalises**, and R3 and R13 both say so. Everything
    else published — names included — is stored as published; folding case here is bought
    deliberately, because case is the single difference R3 rules meaningless for identity.
  - **Trimming is not redundant even though the adapter already trims.** The source's padding is
    stripped on the way in
    ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) R27,
    [FEAT-0009 TASK-0005](../FEAT-0009-contratos-menores-initial-import/TASK-0005-source-port-and-adapter.md)),
    so identifiers reaching this function are already trimmed. It is kept because R3 makes the
    same reduction govern **what a user types** — R8's identifier lookup — and because a function
    that is correct only when its caller has already trimmed is one that silently mismatches the
    day a caller has not. It costs one call.
- **The emptiness test** — an identifier absent, or empty once surrounding whitespace is ignored,
  is **unusable**: it canonicalises to nothing at all. Nothing beyond emptiness is validated; the
  source publishes irregular but genuine identifiers, and rejecting them would discard real
  awards. Trimming upstream means a whitespace-only identifier normally arrives already absent;
  this function still handles it, for the same reason.
- **The rank** — a comparable pair over a contract: its publication date (**null
  ranks last**), then its source identifier (**higher wins**). This is what R4 means by
  *most recently published*, made total and deterministic rather than "some tie-break". It ranks
  **names only**: the identifier is canonical and identical from every contract, so there is
  nothing about it to rank.
- No I/O, no bean, no annotation: these are functions over values, and they are what the rest of
  the feature is tested against.

## Acceptance criteria
- Identifiers differing **only** in surrounding whitespace or letter case canonicalise to the
  **same** value, and that value is `B12345678` — upper-cased, not lower-cased and not the
  spelling any one of them carried.
  ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #3, matching half, #7)
- Canonicalising is **idempotent**: an already-canonical identifier passes through unchanged, so
  a value re-read from the store and re-canonicalised is the same value. (SPEC-0006 #7)
- Identifiers differing in **internal spacing, punctuation, or any character** canonicalise to
  **different** values, and the difference survives in the result — a punctuated identifier keeps
  its punctuation. Over-merging is asserted as its own case, not inferred from the merge case
  passing. (SPEC-0006 #4)
- An absent identifier, an empty one and a whitespace-only one are all **unusable**; an
  irregular but non-empty one — a foreign VAT number, a malformed NIF — is **usable** and
  reduces like any other. (SPEC-0006 #9)
- The rank orders a dated contract above an undated one however late the undated one arrives,
  and breaks a date tie by the higher source identifier; comparing a contract with itself
  is not a win, so re-running a comparison never flips a decision. (SPEC-0006 #7)
- Unit-tested on values alone — no database, no HTTP, no Micronaut context.
