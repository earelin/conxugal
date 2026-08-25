---
feat: FEAT-0015
domain: backend
adrs: [0002, 0023]
status: done
depends_on: []
---

# Extract `ResolveOperador`

Lift SPEC-0006 R3's resolution and R4's name ranking **out of** `StoreContratosMenoresBatch` into a
collaborator both families call. A pure refactor of shipped code: it changes where the derivation
lives, never what it derives, and **it needs nothing from this feature**, so it can land first.

Operadores are the stored projection of
[ADR-0023](../../architecture/0023-operadores-as-a-stored-projection.md), and the collaborator is a
domain service under [ADR-0002](../../architecture/0002-hexagonal-architecture.md) with no transport
and no SQL of its own.

**Why extract rather than write a second copy.** The derivation ships and works —
`contrato_menor.operador_economico_id` is written today — but it lives *inside*
`StoreContratosMenoresBatch` (`operadorAwarded`, and the `account` path driving `NomeRank.outranks`,
`promoteName` and `retainName`). The rule is subtle: nulls-first so undated contracts rank last, a
strict win so a replayed batch cannot make a name flap, promote-before-retain so a displaced name is
not silently dropped. A divergent second copy is a realistic defect, not a theoretical one, and it
would show up as an operador displaying different names depending on which family last touched it.

**This was one task with two other changes and is now three.** What a licitación award supplies as
its rank identity is
[TASK-0021](TASK-0021-settle-the-licitacion-contract-identity-rank.md) — which has no business
hiding inside a refactor, and which settled it by amending SPEC-0006 rather than by widening
`NomeRank` — and resolving the licitacións bidders is
[TASK-0022](TASK-0022-resolve-the-bidders.md), which is the only one of the three that needs
anything from this feature.

## Scope

- **A `ResolveOperador` collaborator** in the domain, holding what `operadorAwarded` and `account`
  hold today: look up by canonical fiscal identifier, insert when absent, and account for the
  published name against `NomeRank` — promoting or retaining, with promote-before-retain preserved.
- **`StoreContratosMenoresBatch` calls it** and keeps its own `@Transactional` boundary, its
  `lastReadingPerSourceId` collapse and its batch shape. The collaborator is called inside that
  transaction, so the second contract of a batch naming a new operador still reads what the first
  wrote.
- **No behaviour change to contratos menores.** That is the criterion this task lives or dies by.
- The collaborator takes the values the rule needs — the published identifier, the published name
  and a `NomeRank` — rather than a `ContratoMenorSourceEntry`, so a second family can call it
  without borrowing the first family's row type.

**Out of scope:** what a licitación supplies as its rank identity (TASK-0021), any licitacións
bidder or awardee
(TASK-0022, [TASK-0012](TASK-0012-resolve-the-awardee.md)), and any change to what
`FiscalIdentifier.of` accepts
([TASK-0019](TASK-0019-widen-fiscal-identifier-to-reject-placeholders.md) owns that).

## Acceptance criteria

- **Every existing contratos menores and operadores test passes unchanged** — not merely green, but
  with no expectation rewritten. The extraction is a move, and a changed expectation is the smell
  that says it was not.
  ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #33)
- `StoreContratosMenoresBatch` contains no operador lookup, insert, promotion or retention of its
  own: it **references neither `promoteName` nor `retainName`**, and `operadorAwarded` and `account`
  are gone from it. (*Stated as "no caller outside `ResolveOperador`" rather than as a grep, since
  both are `OperadorRepository` methods that `JdbcOperadorRepository` implements and its own
  integration tests exercise.*)
  ([ADR-0002](../../architecture/0002-hexagonal-architecture.md))
- An award naming an operador no contract named before **creates** it, catalogued under the name
  that award published; one naming a catalogued operador does not duplicate it. (SPEC-0006 #33)
- An award whose published identifier is unusable yields **no** operador and no invented one.
  (SPEC-0006 #8)
- Re-running the same batch changes nothing: no duplicate operador, and no name flap — the strict
  win in `NomeRank.outranks` still holds through the collaborator. (SPEC-0006 #36, #37)
- A promotion still retains the displaced name, and in that order, so an operador whose contracts
  publish three names still retains all three. (SPEC-0006 #33)
- Repository ports stubbed with **Mockito**, per the project's convention — no hand-rolled fakes.
