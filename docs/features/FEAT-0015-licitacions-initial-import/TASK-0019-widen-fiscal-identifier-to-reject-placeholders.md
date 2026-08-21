---
feat: FEAT-0015
domain: backend
adrs: [0023]
status: todo
depends_on: []
---

# Widen `FiscalIdentifier` to reject published placeholders

**Amendment 4's code half.** A lone dash and the `TEMP-…` form become **unusable**, so neither can
become an identity.

[SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) R5 originally made an identifier
unusable only when it is *"absent, or empty once surrounding whitespace is ignored"*, and
`FiscalIdentifier.of` implements exactly that — so `of("-")` returns a **present** value today.
Reached through the ordinary bidder path that would catalogue **one** operador holding the fiscal
identifier `-`, carrying the bids of dozens of unrelated consortia under whichever name was
published last, and every `TEMP-` value would become precisely the *"invented or placeholder"*
operador R5 exists to forbid. Both failures are silent. Operadores are the stored projection of
[ADR-0023](../../architecture/0023-operadores-as-a-stored-projection.md), so a corrupt identity
there is a corrupt identity everywhere.

**This is a guard, not a blocker.**
[TASK-0010](TASK-0010-record-parse-bidders-and-consortium-detection.md)'s structural branch already
keeps every measured placeholder away from resolution — all 33 sat on consortium rows, and **0 of
578** single-firm rows carried one. But that is a measured negative over one sample, not a rule the
source states. If a single-firm row ever publishes `-`, this widening is what stops it corrupting
the catalogue.

**Depends on nothing.** It can land first.

## Scope

- **`FiscalIdentifier.of` returns empty** for a published value that reduces to a placeholder: a
  lone `-`, and the `TEMP-` form. Both were observed; neither is an identifier.
- **The canonical constructor is left permissive**, rejecting only the empty value it rejects today.
  This is deliberate and it is the opposite of what an earlier draft of this task specified.

  Two production paths construct a `FiscalIdentifier` **from a persisted value**, not from published
  input: `FiscalIdentifierConverter.convertToEntityValue`, and `JdbcContratoMenorRepository`'s row
  mapper reading `operador_economico.fiscal_id`. A throwing constructor turns a **data** condition
  into a **read-time crash** — if any environment already holds an operador whose identifier is `-`,
  the browse read stops working rather than degrading. The 0-of-578 measurement is over *licitacións
  bidder rows*; nothing measures contratos menores awardee identifiers, and this task's own scope
  concedes that the shipped path *would* have created such a row.

  So `of` is the gate for **published input**, which is the only place the rule needs to apply, and
  rehydrating what is already stored stays possible. The cost is that a caller could construct one
  directly; every caller that takes published input goes through `of`, and this task adds no second
  path.
- **The rule is narrow and stays narrow.** Nothing else is validated. The source publishes irregular
  but genuine identifiers — foreign VAT numbers, malformed NIFs — and R5's own reasoning is that
  rejecting them would discard real awards.
- **A contrato menor whose published identifier is `-` now yields no operador** where it previously
  yielded one. Behaviour change on a shipped path, and the intended one: the alternative is a shared
  identity pooling unrelated suppliers.
- **The three residual paragraphs in FEAT-0010's README** are corrected with it. The R5 note itself
  already carries the widening — it landed with the amendment — so what is left is: the trailing
  sentence *"Nothing beyond emptiness is validated"* (line 179), and the two unqualified sequencing
  citations at lines 323 and 343. An earlier draft of this task claimed the whole correction was
  outstanding, which made one of its acceptance criteria pass before any work.

**Out of scope:** any change to how a consortium is detected (TASK-0010's, and it fires first
regardless), and any backfill or migration. If an operador holding `-` turns out to exist, removing
it is an administrator's act under R15.

## Acceptance criteria

- `FiscalIdentifier.of("-")`, `of(" - ")`, `of("TEMP-00934")` and `of("temp-00934")` all answer
  empty. ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #8)
- **`new FiscalIdentifier("-")` still constructs**, so a row already persisted under that value can
  be read back rather than crashing the read. `new FiscalIdentifier("")` still raises.
- A genuinely irregular identifier still resolves through `of`: a foreign VAT number, a malformed
  NIF and a value merely containing a dash (`X-1234567Z`) are all **usable**. The widening rejects
  the two published placeholder forms and nothing else. (SPEC-0006 #9 as qualified)
- A contrato menor published with `-` as its awardee identifier stores with **no operador**, and no
  operador holding `-` is created. Asserted through the shipped caller. (SPEC-0006 #8)
- Every existing contratos menores and operadores test passes, apart from any that asserted the old
  behaviour for `-`, which is updated with a note saying why.
- FEAT-0010's README no longer says *"Nothing beyond emptiness is validated"*, and its two
  sequencing citations to SPEC-0006 #8 and #9 are qualified to match the amended R5.
- Unit-tested; no database and no HTTP.
