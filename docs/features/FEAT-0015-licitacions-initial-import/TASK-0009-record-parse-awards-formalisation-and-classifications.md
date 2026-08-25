---
feat: FEAT-0015
domain: backend
adrs: [0002]
status: done
depends_on: [TASK-0004, TASK-0008]
---

# Record parse: the resolution, formalisation, CPV, NUT and lotes tables

Five of the record's six data tables, parsed into
[TASK-0004](TASK-0004-award-points-and-competition-value-types.md)'s types and added to the source
record [TASK-0008](TASK-0008-record-source-port-and-the-labelled-fields.md) answers. The sixth — the
bidder list — is [TASK-0010](TASK-0010-record-parse-bidders-and-consortium-detection.md)'s, because
its consortium branch is a design in its own right.

Parsing only. **Which operador an award belongs to is
[TASK-0012](TASK-0012-resolve-the-awardee.md)'s**; this task's job is to make every value that
resolution needs available, and the formalisation is the one that matters most.

## Scope

- **The resolution table** — `Lote | Part. | Resolución | Adxudicatario | Importe | Data difusión |
  Prazo de execución | Recurso/Prazo` — one award per row, keyed by lote, with a lotless procedure
  publishing one row whose lote cell is `_`. Parsed out: the resolution, its date, the **awarded
  amount**, the execution period as published text, the **published awardee name**, and **the
  `Part.` count for that lote**.

  `Part.` is parsed here rather than in TASK-0010 because this is the task that reads this table,
  and re-parsing it there to fetch one column would duplicate the whole parse. TASK-0010 cross-checks
  its bidder count against the value this task exposes, keyed by the **normalised** lote.

  **The awarded amount comes from here and from nowhere else.** The listing's `importe` is the base
  budget — measured on 822054, whose listing said `3378552.09` while its two lotes were awarded
  `3.052.743,72` and `206.996,66`.
- **The formalisation table** — `Data formalización | Lote | Contratista | Nacionalidade | Importe`,
  and `Data difusión`. This is **the primary route to an awardee's fiscal identifier**: the
  `Contratista` cell carries name and identifier together (`EQUINSE, S.A. A41111220`), per lote, a
  UTE's own included, and it answers for **58%** of all award rows and **95%** of those on a
  formalised procedure.

  **The split**: a trailing token **shaped like a fiscal identifier** is the identifier and the
  remainder is the name. Where the trailing token is not one, the cell yields **no identifier** and
  the row is still a valid formalisation — not a broken record, only one route to an identifier that
  did not answer, so resolution falls to path B. **It does not go to the outstanding ledger.**
- **CPV and NUT** — code, lote and diffusion date each, into TASK-0004's **two** classification
  types. A procedure-wide lote cell yields a **null lote reference even on a procedure that has
  lotes** — that case is 822054, which has two lotes and classifies neither, and it is the departure
  amendment 2 legitimises.

  **The code is not a value on the classification, it names an entry.**
  [TASK-0004](TASK-0004-award-points-and-competition-value-types.md) makes `Cpv` and `Nut`
  vocabularies with tables of their own, so this parse yields the **code** and whatever stores it
  upserts the entry first — matched on the code, never on a description — and hands the
  classification the entry it got back. Same ordering as the state and the three types on the
  procedure itself, and the same reason: the row's foreign key needs an identity that exists.
- **Lotes come from the award table, not the lotes table.** `Relación de lotes` was header-row-only
  on 822054 while `Nº lotes` said `2` and the award table named both; a parse that discovered lotes
  from the lotes table would have found none and **lost both awards**. `Relación de lotes` is read
  for descriptions and estimated values, which are optional extras.
- **Every lote cell is read through TASK-0004's shared normaliser.** Measured over 240 procedures:
  the award, formalisation and NUT tables write `_` for a procedure-wide row; zero-padding varies
  *within* a table (the award table produced both `1` and `05`); and a lote identifier is not always
  numeric (`OU0028`, `LU4001`, `CO0642`).
- **An absent table is an ordinary answer; only a table that is present and unreadable fails the
  record.** Every *En curso*, *Pendente de adxudicar*, *Deserto*, *Anulado* and *Renuncia* procedure
  has no resolution table at all — 278 of the ~2 000 listing rows sampled — and a parse that treated
  absence as failure would send every one of them to the ledger.

**Out of scope:** the bidder list and the cross-check itself (TASK-0010), the awardee resolution
(TASK-0012), and the historical re-read — SPEC-0008 #15 is a later feature's.

## Acceptance criteria

- A two-lote procedure yields two awards, each with its own amount, resolution, date, published
  awardee name and `Part.` count, and **no procedure-level award**.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #9 as amended)
- A lotless procedure yields one award, against the procedure, with its lote key normalised from
  `_`. (SPEC-0008 #9)
- **The `Part.` count is exposed per normalised lote key**, so TASK-0010 can cross-check against it
  without re-parsing this table. (SPEC-0008 #19)
- The awarded amount is read from the resolution table; a test asserts that the listing's `importe`
  for the same procedure is **not** what the award holds. (SPEC-0008 #10)
- `EQUINSE, S.A. A41111220` splits into the name `EQUINSE, S.A.` and the identifier `A41111220`; a
  `Contratista` cell whose trailing token is not identifier-shaped yields the whole cell as the name
  and **no identifier**, and the formalisation still parses. (SPEC-0008 #46)
- A CPV row whose lote cell is `_` on a procedure with two lotes yields a classification with **no
  lote reference**. This is procedure 822054 and the case a stricter model could not hold.
  (SPEC-0008 #10 as amended)
- A procedure whose `Relación de lotes` is empty but whose award table names lotes `1` and `2`
  yields **two lotes**, with description and estimated value absent. (SPEC-0008 #10)
- A formalisation whose lote cell is `01` attaches to the award row whose cell is `1`, and that
  award carries the formalisation's identifier. The naive join, which would leave the award
  unformalised, is asserted against. (SPEC-0008 #46)
- **A procedure with no resolution, formalisation or classification table parses with none of each
  and does not raise**; a table that is present and unreadable does raise, with
  `LicitacionRecordUnavailableException`. (SPEC-0008 #36 import-and-store half, #41)
- Unit-tested against captured record fixtures — 822054 among them, since it is the procedure four
  of these cases were measured on — with no HTTP and no database.
