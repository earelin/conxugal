---
feat: FEAT-0015
domain: backend
adrs: [0002, 0008, 0023]
status: todo
depends_on: [TASK-0005, TASK-0006, TASK-0009, TASK-0013]
---

# `StoreLicitacion`: reconciling a restated procedure

Every retrieval of a record restates the **whole** procedure, so R13's reconciliation is the
ordinary path rather than an exception. This is the use case that turns one retrieval into one
stored procedure, idempotently, and it is the last piece before the walk
([TASK-0015](TASK-0015-single-organo-initial-import.md)) has something to call.

## Scope

- **`StoreLicitacion` takes the listing entry *and* the parsed record**, together, and stores one
  procedure in **one transaction**.

  Both, because neither is sufficient. Four of the aggregate's fields — publication date,
  last-modified date, state **code** and state **label** — exist only on the listing entry; the
  record publishes the state's *label* alone, which for codes 101 and 102 is ambiguous. An earlier
  draft took "one parsed record" and left those four with no supplier, which would have shown up as
  a `NOT NULL` failure or a silently empty column.

  On the **ledger retry path** there is no listing entry, which is why
  [TASK-0002](TASK-0002-licitacions-per-organo-import-state.md)'s ledger carries those four values
  with the identifier. This use case takes them from whichever source the caller has.
- The procedure is matched by its **publication identifier** — the natural key, not the aggregate's
  identity — and refreshed in place; its children are reconciled to what the record now publishes.
- **What the record no longer publishes is retained and marked withdrawn** — a lote, a
  classification, a bidder, an award, a **formalisation** or a UTE membership. It then appears in no
  list, history or total. Nothing an import does deletes a row.

  The formalisation is in that list because it is precisely the row whose disappearance produces the
  supersession case below, and an earlier draft omitted it.

  **This is not tidiness.** SPEC-0006 rests the reversibility half of its R12 privacy analysis on
  every feeding family's removal rule being non-destructive and reversible; a participation an
  ordinary import could erase, with no administrator act and no way back, would break that promise
  for the whole catalogue.
- **A membership's visibility follows its participation's.** SPEC-0006 R7 counts "one visible UTE
  membership" toward an operador's reachability, so a member firm whose only tie is a membership
  under a withdrawn participation would stay reachable through an **invisible** fact — which is what
  SPEC-0006 #39 tests for.
- **A licitación absent from a later import is retained unchanged** (R14). Absence is not evidence
  of withdrawal, and the explicit removal that *is* (R15) is a later feature's. Nothing here
  compares the store against the listing and withdraws the difference.
- **The awardee link is re-resolved on every restatement, and a write that would lower the
  resolution path is refused.** The order is
  [TASK-0004](TASK-0004-award-points-and-competition-value-types.md)'s:

  ```text
  PUBLISHED_BY_FORMALISATION > PUBLISHED_BY_BIDDER > NAME_DERIVED > UNRESOLVED
  ```

  Re-running [TASK-0012](TASK-0012-resolve-the-awardee.md)'s routing is free — it is a pure function
  of the record. What this task adds is the **gate**: compare the newly computed path against the
  **stored** one and write only where it does not go down. Without the gate, "a published identifier
  supersedes a derived one" is only true within a single resolution, where A-before-B-before-C
  already gives it.

  **This deliberately outranks R13's refresh-to-what-is-published rule for this one field.** A
  formalisation withdrawn at the source would otherwise demote a published link back to a derived
  one or to nothing, and an awardee the source once named would be forgotten. The identifier was
  published; the withdrawal of the row that published it is not evidence that it was wrong.

  It is also the whole mechanism behind the historical tail closing: a procedure moving from
  *adxudicado* to *formalizado* gains a formalisation, advances its last-modified date, and the run
  that re-reads it replaces a name-derived link — or no link at all — with a published one.
- Storing an already-stored procedure with **no change** changes nothing observable: no new rows, no
  flapping operador name, no withdrawal, and no counted update beyond the refresh.

**Out of scope:** the walk, the cursor, the ledger, and any comparison across procedures. This use
case sees one procedure at a time and knows nothing about the Órgano's history as a whole.

## Acceptance criteria

- Storing the same listing entry and record twice leaves the stored set and every attribute
  unchanged, and the second store reports nothing added.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #17)
- **The stored procedure carries the listing entry's `estado` code, its label, and both dates** —
  none of which the record publishes. (SPEC-0008 #7 per-field half, #44)
- **A store driven from a ledger entry, with no listing entry available, produces the same
  procedure** as one driven from the listing. (SPEC-0008 #41)
- A restated record whose attributes changed refreshes the procedure **in place**: same identity,
  same row, new values. (SPEC-0008 #16 import half)
- A lote, a classification, a bidder, an award and a **formalisation** each present in the first
  record and absent from the second are **retained and marked withdrawn**, not deleted — verified by
  reading the rows back. (SPEC-0008 #16)
- A withdrawn participation's memberships are withdrawn with it, and a member firm whose only tie
  was that membership is no longer reachable through it.
  ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #39)
- A procedure that moves from *adxudicado* with a `NAME_DERIVED` awardee to *formalizado*
  re-resolves to the identifier the formalisation publishes, marked
  `PUBLISHED_BY_FORMALISATION` — **even when that is a different operador**. (SPEC-0008 #46)
- **A restatement that would lower the resolution path does not write**: a stored
  `PUBLISHED_BY_FORMALISATION` link survives a later record whose formalisation is gone and whose
  routing answers `NAME_DERIVED` or `UNRESOLVED`, and a stored `PUBLISHED_BY_BIDDER` survives a
  `NAME_DERIVED`. (SPEC-0008 #46)
- A licitación stored by an earlier run and absent from the record set of a later one is **retained
  unchanged** — not withdrawn, not touched. (SPEC-0008 #16)
- A store that fails part-way leaves nothing partially written: the procedure and every child commit
  together. Integration-tested against PostgreSQL (Testcontainers).
- The idempotence, withdrawal and supersession cases are integration tests; the re-resolution
  routing is unit-tested with the ports stubbed (Mockito).
