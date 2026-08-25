---
feat: FEAT-0015
domain: backend
adrs: [0002]
status: done
depends_on: [TASK-0004, TASK-0008, TASK-0009]
---

# Record parse: bidders, consortium detection and the `Part.` cross-check

The record's sixth data table, and the one decision this feature most depends on getting right: **a
bidder row is classified as a consortium by its markup, never by its name and never by its
identifier**.

Parsing only — what is *stored* for a consortium is
[TASK-0013](TASK-0013-consortia-and-their-membership.md)'s, and resolving a single-firm bidder to
an operador is [TASK-0022](TASK-0022-resolve-the-bidders.md)'s. It depends on
[TASK-0009](TASK-0009-record-parse-awards-formalisation-and-classifications.md) for the `Part.`
count the cross-check compares against.

## Scope

- **Bidder rows** (`tr.filaLic_*`) — `Lote | NIF | Nome` — parsed per lote, with the lote read
  through [TASK-0004](TASK-0004-award-points-and-competition-value-types.md)'s shared normaliser.
  Measured over 240 procedures, the bidder table writes **`-`** for a procedure-wide row where the
  award table writes `_`, which is why the join cannot compare raw cells.
- **A consortium is detected by its nested `<ul>`.** A UTE cell nests a second list inside the
  first, naming each member with its own identifier:

  ```html
  <tr class="filaLic_1_1 filasLicitadores hidden">
    <td>1</td>
    <td>-</td>
    <td>
      <ul class='list-unstyled'>
        <li>UTE PRACE-TABOADA RAMOS</li>
        <ul>
          <li>A70319678 - PRACE SERVICIOS Y OBRAS SA</li>
          <li>B94181807 - CONSTRUCCIONES Y OBRAS TABOADA RAMOS SLU</li>
        </ul>
      </ul>
    </td>
  </tr>
  ```

  Note that this row's **lote** cell is `1` and the `-` is its **NIF** cell — the two are easy to
  confuse and an earlier draft of this feature did.

  Measured over **613 bidder rows in 250 procedures**, the structural test was **exact** — never
  firing on a single-firm bidder, never missing a consortium. The alternatives are not close: a name
  test beginning `UTE` misses **7 of 35** (`MISTURAS-INGESAN` among them), and the `U`-prefix
  identifier test misses **33 of 35**.

  This is not the inference SPEC-0006 R6 forbids. **The markup is the publication**, which is
  precisely what R17's own *"membership is published, not inferred"* asks for.
- **The branch is taken before any identifier is read**, and that ordering is load-bearing. `-` and
  `TEMP-…` appeared on **33 of 35** consortium rows and **0 of 578** single-firm rows; because the
  structural branch fires first, a placeholder is never offered for resolution, which would
  otherwise catalogue one operador holding the identifier `-` for dozens of unrelated consortia.
  [TASK-0019](TASK-0019-widen-fiscal-identifier-to-reject-placeholders.md) is the belt to this
  braces, not a substitute for it.
- **A consortium yields**: its published name, its own published identifier where it has one (2 of
  35 **on bidder rows** — the formalisation identifies some of the rest, which is TASK-0013's), and
  its **member entries** parsed out of the inner list. All **80** member entries measured carried an
  ordinary identifier.

  **This is what the source published, not a decision about the catalogue.** Whether the consortium
  becomes an operador holding that identifier or one keyed on its bid is TASK-0013's, and it needs
  the formalisation as well as this row to decide — so nothing here resolves, mints or catalogues
  anything.
- **The `Part.` cross-check.** TASK-0009 exposes the award table's `Part.` per normalised lote. A
  parse producing a different bidder count **for a lote whose bidder table was published** has
  failed, and the procedure raises rather than yielding a short list: a silently short bidder list is
  indistinguishable from a genuine one and would understate competition for ever.

  Measured over 240 procedures: joined on the raw cell the check disagrees on **95 of 158** award
  rows, every failure an artefact of `_` against `-` or `05` against `5`; joined on the normalised
  key it agrees **158 of 158**. Unnormalised, the check would fail most procedures the source
  publishes perfectly well.
- **An absent bidder table is an empty list, whatever `Part.` says, and does not raise.** 26 of the
  first 70 procedures sampled had no bidder table at all — open, pending, deserted or withdrawn —
  and nothing measured rules out an award row carrying a non-zero `Part.` on such a page. The
  cross-check applies **only to a lote whose bidder table was published**; the precedence is stated
  because the two rules otherwise disagree in silence.

**Out of scope:** resolving any bidder to an operador, storing anything, and the `Part.` figure's own
storage — it is a cross-check, not a stored count.

## Acceptance criteria

- The fixture above yields **one** bidder recognised as a consortium, carrying the published name
  `UTE PRACE-TABOADA RAMOS`, the lote `1`, and **two** member entries with their own identifiers.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #21 as amended)
- A consortium named `MISTURAS-INGESAN` — no `UTE` prefix — is detected identically, because the
  test is the markup. A name-prefix test would record it as a single firm bidding under a
  placeholder identifier, and this asserts against exactly that. (SPEC-0008 #21 as amended)
- **A consortium row carries no fiscal identifier whatever its NIF cell holds** — `-`,
  `TEMP-00934`, or a real `U…` — and yields no single-firm bidder. The `U…` case yields the
  identifier as the *consortium's own*, which is a different field.
  (SPEC-0008 #20, #21 as amended)
- A single-firm bidder row is never classified as a consortium, across a fixture set covering every
  row shape measured. (SPEC-0008 #19)
- A lote whose `Part.` says 10 and whose published bidder table holds 9 rows **raises**; one where
  they agree yields the list. (SPEC-0008 #19, #41)
- The cross-check joins on the normalised lote key: a lotless procedure whose award row writes `_`
  and whose bidder rows write `-` **agrees** rather than failing. (SPEC-0008 #19)
- **A procedure whose award rows carry a non-zero `Part.` and which publishes no bidder table
  parses to an empty bidder list and does not raise.** (SPEC-0008 #36 import-and-store half)
- Unit-tested against captured record fixtures, with no HTTP and no database.
