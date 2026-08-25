---
feat: FEAT-0015
domain: backend
adrs: [0008, 0023]
status: done
depends_on: [TASK-0011]
---

# Settle the licitación contract identity in the name rank

SPEC-0006 R4 breaks a name tie by *"the higher contract identifier"*, and `NomeRank` is
`(date, sourceId)` over one `BIGINT`. That is total for contratos menores and **not** for
licitacións: SPEC-0006 records a licitación's contract identity as a publication identifier
**together with a lote**, so two lotes of one procedure awarded to the same operador under two
published spellings tie **exactly** — same date, same identifier — `outranks` answers false in both
directions, and the displayed name is whichever the import accounted for first.

This task exists to settle that, and it settles it **the way the earlier draft named as the
alternative**: not by widening `NomeRank`, but by *"an explicit decision that R4's tie-break is
per-family after all, which would need SPEC-0006 amended rather than worked around"*. **It is
documentation only** — no migration, no SQL, no change to `NomeRank` and no change to any test.

## What was decided

### 1. The rank keeps its pair; SPEC-0006 is amended

**R4's tie-break ranks on the publication identifier alone.** The lote is not carried into the
rank, and the pair it cannot separate is settled by whichever award the import accounts for first.
That is stable across re-imports so long as the source publishes a procedure's awards in a stable
order, but it is not derivable from the two contracts' values, and the spec now says so instead of
claiming otherwise.

**Three conditions have to hold at once for the case to be reachable**, and the third is what makes
it rare: the procedure has lotes, the same operador is awarded two of them, *and* the two award rows
spell its name differently. Two rows spelling it identically leave nothing to choose between. The
only two-lote capture held in the repository — 822054 — awarded its lotes to two different firms.

**The cost is which spelling displays, and nothing else.** R3 makes the fiscal identifier the
operador's identity and this leaves it untouched, so nothing is split or merged, no history row
moves, and no count or total changes. Against that, carrying the lote into the rank is a migration
over two shipped, populated tables, both hand-written SQL row-value comparisons in
`JdbcOperadorRepository` re-indexed, and every construction site of a record the contratos menores
tests build in dozens of places. A per-family discriminator was never the alternative: the two
families share one publication id space (measured), so there is nothing to disambiguate except the
lote itself.

**Nothing else about the lote changes.** It is still parsed, still stored on the lote, the award and
the participation, and still what SPEC-0006 R9 rows an operador's history by — which is the reason
it is part of the contract identity at all. Only the *name rank* declines to carry it.

**If it ever proves to matter, `ResolveOperador` is the single place that changes.** Ordering on
`(date, sourceId, name)` settles the pair by value rather than by arrival, needs no column and no
migration, and leaves contratos menores provably unaffected — they never tie on `(date, sourceId)`
to begin with, since a contrato menor *is* one publication. Recorded as the cheap way back, not as
work taken here.

### 2. The identifier stays a `long`, parsed at the edge

[TASK-0003](TASK-0003-licitacion-domain-model.md) holds a licitación's `publicationId` as **text**,
not `long`, so that a source which stopped minting numeric identifiers costs a parse rather than a
migration. `NomeRank.sourceId` is a `long`, so a licitación's identifier does not fit it, and
comparing identifiers **as text** is worse than the problem: it makes `"9"` outrank `"10"` and would
silently corrupt the tie-break for the shipped contratos menores family, whose ranks are already
populated.

**So `sourceId` stays a `long` and the parse happens at the edge**, which is exactly the cost
TASK-0003 signed up for when it chose text — *"an identifier that stopped being numeric then costs a
parse at the adapter instead of a column type"*. The source is measured to mint integers (18 700 →
829 000) and both families draw from **one publication id space**
([`design/source-contract.md`](design/source-contract.md)), so a licitación's identifier compares
against a contrato menor's numerically and no family discriminator is needed. A publication
identifier that is not a number has no rank; none is observed, and it is a defect rather than a
supported case.

**No accessor is added here.** `PublicationId` → `long` arrives with its first caller,
[TASK-0012](TASK-0012-resolve-the-awardee.md), rather than shipping unused.

## Scope

- **[SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md)** — the family-supply list splits
  the two uses of a licitación's two-part identity; **R4** states that the tie-break ranks on the
  publication identifier alone and what follows for the pair it cannot separate; **#36** stops
  claiming such a pair is settled by construction.
- **[SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md)** — the contract-identity bullet
  keeps R9's reason for the lote and drops R4's; *What this spec requires of its sibling specs*
  records the amendment as a third round.
- **This feature's README** — *The name rank keeps its pair* replaces *The name-rank identity gains
  a lote*.
- **`Licitacion`'s javadoc**, which deferred the identifier comparison to a later task, states the
  settled answer instead.
- **[TASK-0012](TASK-0012-resolve-the-awardee.md) and
  [TASK-0022](TASK-0022-resolve-the-bidders.md)** no longer depend on this task, there being no
  migration to wait for.

**Out of scope:** any change to `NomeRank`, to `JdbcOperadorRepository`, to the schema, or to any
test — and resolving any licitacións bidder or awardee, which is TASK-0012's and TASK-0022's.

## Acceptance criteria

- SPEC-0006 no longer asserts that a name tie between two contracts sharing a date **and** a
  publication identifier is settled by construction, and states what is settled instead.
  ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #36)
- SPEC-0006 R4 states that the tie-break ranks on the publication identifier alone, names the family
  that can produce a tie under it, and records why the case is accepted. (SPEC-0006 #36)
- SPEC-0008 still requires the lote as part of the contract identity **for R9's history rows**, so
  an operador awarded two lotes of one procedure still holds two rows. (SPEC-0008 #23)
- The retention's idempotence is unaffected and is not restated as changed: two spellings at an
  equal rank leave `outranks` false and the retain upsert a no-op, so a re-import writes nothing.
  (SPEC-0006 #37)
- `docs/` lints clean, and no document links to this task under its former filename.
