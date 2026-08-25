---
feat: FEAT-0010
domain: backend
adrs: [0002]
status: done
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

> **Five notes from the implementation.**
>
> - **The canonical form is a type, not a function's return value.** `FiscalIdentifier` is a value
>   object that is canonical by construction, so every way of building one reduces and no instance
>   can hold a published spelling. `OperadorEconomico` holds that type and `OperadorRepository`
>   looks up by it, which is what makes "the store is unique on the canonical form" unstateable in
>   any other form rather than merely remembered.
> - **This costs the "no annotation, no bean" line in the Scope below.** Being an aggregate's
>   column, the type carries a `@TypeDef` and needs an `AttributeConverter` — the pattern
>   `Money` and every typed identifier already follow
>   ([ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md),
>   [ADR-0019](../../architecture/0019-typed-aggregate-identifiers.md)). The rules themselves stay
>   pure and are still tested on values alone; the annotation is the price of the encapsulation
>   being real at the boundary rather than only in the middle.
> - **[TASK-0002](TASK-0002-operador-domain-model.md) landed ahead of this task**, so
>   `OperadorEconomico` already carried the canonicalisation inline. It no longer canonicalises or
>   rejects anything: the type does, so the rule is stated once rather than in two places kept in
>   step by hand. "No entity yet" below describes the order the pieces were written in, not the
>   order they landed.
> - **Trimming uses the codebase's one definition of surrounding whitespace**, which is broader
>   than `String.strip`: it also counts a non-breaking space and the separator controls as padding.
>   The identifier is what the catalogue is unique on, so the broader rule is the safer one, and it
>   is already the definition every other stored text value uses.
> - **The rank comparison sits on `NomeRank` itself**: the pair is `Comparable`, and
>   `candidate.outranks(incumbent)` is the predicate the import asks. TASK-0002 shipped a javadoc
>   line saying the comparison would live outside the pair; it reads better on it, since the pair
>   is the only thing the rule is about and is ranked no other way, so that line is corrected
>   rather than left standing. R4's order being the type's *natural* order is what lets a later
>   read surface sort retained names with no comparator of its own — and it is consistent with
>   `equals`, so a sorted set holds what the record's own equality says it does. `outranks` stays
>   because **an undated rank sorts first**: the name to display is the `max` of a collection and
>   never the first element of a sorted one, and a predicate cannot be read in the wrong
>   direction.
> - **The criterion TASK-0002 deferred here is proved**: a retained name orders against the
>   aggregate's own rank through this same comparison, so the two cannot disagree.
>   ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #36)

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

  *Since amended.* The pair is total and deterministic for contratos menores, whose contracts are
  one per publication. R4 as it now stands admits a family that makes several contracts per
  publication and ties in this pair; see
  [FEAT-0015 TASK-0021](../FEAT-0015-licitacions-initial-import/TASK-0021-settle-the-licitacion-contract-identity-rank.md).
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
