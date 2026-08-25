---
feat: FEAT-0016
domain: backend
adrs: []
status: todo
depends_on: [TASK-0001, TASK-0003]
---

# Correct the two V19 comments this feature falsifies

Two comments FEAT-0015 shipped about its own columns are made false by the reads
[TASK-0001](TASK-0001-selection-value-types-and-read-ports.md) and
[TASK-0003](TASK-0003-paged-ordered-counted-reads.md) build. This task corrects them, landing with
the reads rather than after them.

It is documentation-only against shipped SQL — no schema change, no behaviour change — and it exists
as a task because both comments are the kind a task author reads as a rule. The precedent is
FEAT-0015's own
[TASK-0019](../FEAT-0015-licitacions-initial-import/TASK-0019-widen-fiscal-identifier-to-reject-placeholders.md),
which corrected [FEAT-0010](../FEAT-0010-operadores-economicos-base/README.md)'s README with the
change rather than leaving it recording a superseded rule.

## Scope

### 1. `licitacion.publication_id` is ordered now

V19's comment says the column "is matched on and **never ordered**, summed or incremented -- so text
gives up nothing here", and `PublicationId`'s javadoc says the same, adding that "one caller does need
it *ordered*" and names only the operador name rank.

[SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #30 makes it the **tie-break of every
ordering** this feature offers, so it is now ordered by every read a user can reach.

Both are corrected to say so, and to record **what the text ordering costs**: among two procedures of
one Órgano, published on the same day, whose identifiers straddle a digit-count boundary, the order is
**lexicographic rather than numeric** — `"99812"` precedes `"100403"` descending. The measured
identifier range is 18 700 → 829 000, so the case arises on the day the source crossed 100 000.

**The column is not changed and the decision is not reopened.** #30's stated requirement is a
determinate order, which a `UNIQUE` text column gives; the feature README records why a generated
numeric column was considered and rejected, and the correction points at that rather than repeating
it. The comment's *conclusion* — that `TEXT` is the right storage — survives; only its *reason*
needs amending, and a reason that is false is worse than none.

### 2. `licitacion_award.awardee_name` is not what a reader is shown

V19's comment reads: "The name the resolution published, kept beside the link rather than instead of
it: **an award no route resolves still names somebody, and that is what a reader is shown.**"

SPEC-0008 says the opposite in three places:

- **R25** — where the awardee could not be resolved, "the licitación shows an award and **names
  nobody**";
- **#20** — the same, as a criterion;
- **#24** — unqualified: "This family holds **no per-row name at all**, for any party — including a
  consortium the source does not identify, whose published name is held on the operador it is
  catalogued as."

And **R21** reduces the cases to two — "catalogued and reachable, or not shown at all" — while **R20**
adds that "nothing here is a route that dead-ends: a party R16 could not resolve is simply **not
counted** among the awardees the row states."

**The column is not removed and its first clause is right.** FEAT-0015's path C re-resolves an awardee
by matching the published name on every restatement, which is the mechanism that closes the historical
tail when an old *adxudicado* procedure finally formalises — so the name genuinely is kept beside the
link rather than instead of it. What is wrong is only the claim about **display**.

The comment is corrected to say that it is a **resolution input, never a rendering value**, and that
**no read selects it**. The correction matters because the two readings differ on 36% of award rows
and on almost all of the pre-2013 tail an initial import spends its time on: rendering the column
would put a name on a row with no operador behind it — the row R20 forbids — and would reintroduce
the per-row name SPEC-0008's first amendment removed by making an unidentified consortium an operador
so its name could live where every other party's does.

**Out of scope:** any schema change, any change to what is stored, and FEAT-0015's README, whose own
text does not make either claim.

## Acceptance criteria

- V19's `publication_id` comment no longer asserts the column is never ordered, names this feature's
  tie-break as the ordering caller, and records the lexicographic reading and its bound.
  (SPEC-0008 #30)
- `PublicationId`'s javadoc is corrected in the same terms, so the two documents that carried the
  claim agree.
- V19's `awardee_name` comment no longer asserts the name is what a reader is shown, and states that
  it is a resolution input which no read selects. (SPEC-0008 #20, #24)
- **A test enforces the second correction rather than only documenting it**: an assertion over the
  statements [TASK-0003](TASK-0003-paged-ordered-counted-reads.md) builds proves that
  `awardee_name` appears in none of them, so a later author cannot quietly add it back. (SPEC-0008 #24)
- No migration is added, no column is altered, and `scripts/docs-lint.sh` passes.
