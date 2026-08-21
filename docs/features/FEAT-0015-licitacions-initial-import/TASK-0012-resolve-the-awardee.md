---
feat: FEAT-0015
domain: backend
adrs: [0002, 0023]
status: todo
depends_on: [TASK-0009, TASK-0022]
---

# Resolve the awardee: formalisation, then bidder list, then catalogue

An award row names its awardee in text and **publishes no fiscal identifier** — over 119 award rows,
not one carried one. Three routes reach one, and the order matters because **only the last infers**.

This is **amendment 3**, and its measured shape over **284 award rows** is what makes it small:

| Route | | Award rows | Share |
| --- | --- | --- | --- |
| **A** | the **formalisation** publishes it | 164 | **58%** |
| **B** | the procedure's **bidder list** publishes it | 19 | 7% |
| **C** | name only — a catalogue match is the sole route | 101 | 36% |

**64% is published (183 of 284)**, and **96%** of awards on a *formalizado* procedure resolve
without inferring anything. What needs a name match is a **historical tail**: counted in
*procedures* over a separate pass, 60 of 73 award-bearing *adxudicado* procedures had no identifier
recoverable by any route, and 59 of those 60 were published 2008–2012. *(Award rows and procedures
are different populations and are not summed against each other.)*

## Scope

- **Path A — the formalisation.** Take the identifier
  [TASK-0009](TASK-0009-record-parse-awards-formalisation-and-classifications.md) split out of the
  `Contratista` cell, joined on the **normalised** lote key. Not inference: the source publishes the
  name and the identifier side by side.
- **Path B — the procedure's own bidder rows.** Match the award's published name against the
  bidders' published names and take the identifier that bidder published. Also not inference — every
  value compared is on the page.
- **Path C — a unique match against the operadores catalogue.** The **only** inferring step, and it
  is bounded: it **never creates an operador**, links only where **exactly one** catalogued operador
  matches, and the award records the link as `NAME_DERIVED` so it stays distinguishable and
  reversible. Measured ambiguity is 1 name in 268, and that one is a source typo.

  The match is against an operador's **principal name and its retained alternatives**, since R15's
  retained set is what makes a firm findable under a name it no longer displays — but the uniqueness
  test is over **operadores**, not over names: two names belonging to the *same* operador are one
  match, and one name borne by *two* operadores is ambiguous and declines.
- **Both name-matching steps require a unique match.** B is C's comparison scoped to one procedure,
  so an ambiguous B is no more usable than an ambiguous C, and a second bidder with the same name
  means B does not answer.
- **An award whose awardee is a consortium row takes no path here.**
  [TASK-0013](TASK-0013-consortia-and-their-membership.md) attributes it, because whether the
  consortium is catalogued is a property of the procedure rather than of the award row. Stated in
  scope rather than left to luck: path C would otherwise try `UTE PRACE-TABOADA RAMOS` against the
  catalogue, which matches nothing today and is not a design.
- **Normalisation for matching is not normalisation for storage.** The comparison folds case,
  accents, punctuation and surrounding whitespace, and is used for **nothing but the comparison**.
  R33 stores every value as published; nothing normalised is stored or displayed.
- **Where the formalisation names a different party than the resolution, the award's name governs
  and path A is not taken.** The resolution states who was awarded; a formalisation naming someone
  else is a fact about signing, and attributing the award to that party would put money against an
  operador the source never awarded it to.
- **No route hits: the award is stored holding no operador**, marked `UNRESOLVED`. R16 and R25
  already say a licitación may show an award and name nobody, and R25 refuses to make a resolvable
  awardee a condition of visibility. An unresolved awardee costs a link, never a procedure — and
  never the Órgano's walk.
- **The resolved award supplies its rank** through TASK-0011's collaborator, with the lote component
  [TASK-0021](TASK-0021-nome-rank-gains-a-lote-component.md) added — this is the caller that needs
  it, and two lotes of one procedure awarded to the same operador is the tie it exists to break.
- The resolution path is written on the award, which is what
  [TASK-0014](TASK-0014-reconciling-a-restated-procedure.md) reads to let a published identifier
  supersede a derived one.

**The honest caveat is recorded rather than engineered around**: path C's catalogue is fed largely
by contratos menores from 2018 onward while the awards needing it are mostly 2008–2012, so the yield
may be modest. And path C's outcome depends on the catalogue **at the moment it runs**, so two
Órganos imported in opposite orders can resolve differently. Re-resolution on restatement is the
convergence mechanism, and it arrives with the incremental feature.

**Out of scope:** consortia (TASK-0013), re-resolution on restatement (TASK-0014), and any operador
**creation** by path C.

## Acceptance criteria

- A formalised procedure whose `Contratista` cell publishes `EQUINSE, S.A. A41111220` resolves its
  award to the operador `A41111220`, marked `PUBLISHED_BY_FORMALISATION`.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #46)
- A procedure with no formalisation whose awardee name matches exactly one bidder row resolves from
  that bidder's identifier, marked `PUBLISHED_BY_BIDDER`. (SPEC-0008 #46, #19)
- Two bidders publishing the **same** name make path B decline; resolution falls through to C.
  (SPEC-0008 #46)
- An awardee matching exactly one catalogued operador by name resolves, marked `NAME_DERIVED`;
  matching two or more, or none, leaves the award holding **no operador** and the licitación
  **stored with no withdrawal marker set**. (SPEC-0008 #20, #46)
- Two retained names belonging to the **same** operador are one match, not an ambiguity; one name
  borne by two operadores declines. ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md)
  #33)
- **Path C never inserts an operador.** Asserted against the repository, because the failure is
  silent and permanent: an invented operador is exactly what SPEC-0006 R5 forbids.
  (SPEC-0008 #46)
- **An award whose awardee is a consortium row is left unattributed by this task** — no path is
  tried and no operador is linked. (SPEC-0008 #21 as amended)
- A formalisation naming a different party than the resolution leaves the award attributed to the
  **resolution's** party — path A is not taken and B or C answers instead. (SPEC-0008 #23, #46)
- A formalisation writing lote `01` resolves the award row writing lote `1`; the naive join, which
  would demote this to B or C, is asserted against. (SPEC-0008 #46)
- Matching folds case, accents and punctuation — `Xestión Ambiental de Contratas, S.L.` matches
  `XESTION AMBIENTAL DE CONTRATAS SL` — and **the stored award still holds the name exactly as
  published**, accents and punctuation intact. (SPEC-0008 #44)
- Two lotes of one procedure awarded to the same operador under two spellings resolve
  **deterministically**: the same name displays whichever order the two are stored in. Before the
  lote joins the rank tuple this fails, which is why TASK-0021 precedes it. (SPEC-0006 #36)
- Every award carries a resolution path, including the unresolved one. (SPEC-0008 #46)
- Repository ports stubbed with Mockito for the routing cases; the catalogue-match and
  rank-determinism cases integration-tested against PostgreSQL with a real operadores table.
