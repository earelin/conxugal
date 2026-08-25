---
feat: FEAT-0016
domain: backend
adrs: [0002, 0008]
status: todo
depends_on: [TASK-0002, TASK-0003]
---

# The year, CPV and state facets

What a reader is **offered** to narrow by. R22 and R23 impose the same rule on all three — only
values the selection actually contains are offered — and give the same reason: choosing one can then
never be the reason a list is empty.

## Scope

- **`YearFacet`s** — the distinct publication years an Órgano has **visible** licitacións in,
  **newest first**. Read before a year is chosen, so it is scoped to the Órgano rather than to a
  selection, and it is what
  [TASK-0005](TASK-0005-the-licitacions-read-use-cases.md) derives the section's very existence from.

  ❗ **It carries `publication_year IS NOT NULL` explicitly.** This is the one browse read with no
  equality test on the year to withhold an undated procedure for free, and `DESC` orders a null
  **ahead of every real year** — so without the conjunct the chooser opens on a year that is not one,
  and `YearSelection`, which has no representable absence, refuses it. Exactly FEAT-0011's trap, in
  exactly the same statement.
- **`CpvFacet`s** — the distinct CPV codes and descriptions the selection's licitacións carry,
  ordered by code. Driven **from the year's licitacións into `cpv`**, never from `cpv` outward: that
  table is a regulated EU list held once and unseeded, and reading it outward would offer thousands
  of codes the Órgano has never published.

  It counts a classification hanging off **a lote or off the procedure as a whole**, matching
  [TASK-0003](TASK-0003-paged-ordered-counted-reads.md)'s filter, and it excludes withdrawn
  classifications and withdrawn licitacións alike. `DISTINCT` here is **not** the #10 duplication
  trap — a facet is a list of codes, not of licitacións, and a code carried by forty procedures is
  one entry.
- **`StateFacet`s** — the states the selection's licitacións are in, as the source's own **code** and
  **label**, ordered by label.

  ❗ **Grouped on the code and the label together.** Grouping on the label alone merges codes **101
  and 102, both published *Histórico***, which is precisely why V19 puts no unique constraint on the
  label. The filter is applied by code, the facet is keyed by code, and the label is what is read
  rather than what is matched. The ordering picks up `COLLATE "galician"` from the column once
  [TASK-0002](TASK-0002-visible-browse-schema-and-indexes.md)'s migration lands.

**It adds nothing to the port.** `VisibleLicitacionRepository` and the facet types are declared whole
by [TASK-0001](TASK-0001-selection-value-types-and-read-ports.md); this task **implements** the two
methods that answer them. An earlier draft split the declaration between the two tasks, which left the
interface uncompilable at TASK-0001's own landing point and hid a cycle `depends_on:` cannot express.

❗ **Two of the three columns these reads select are nullable**, and a fixture with fully populated
vocabularies would never reveal it: `licitacion_state.label` is `TEXT` with no `NOT NULL`, and
`cpv.description` likewise — V19 says of the latter that nothing populates it yet, which **these reads
falsify** ([TASK-0008](TASK-0008-correct-the-two-v19-comments.md) carries that correction). A facet
whose label or description is absent is answered under its **code**, and the ordering places it last.

**Facets are a function of the Órgano and the year alone**, not of the other filter in effect. R23's
preceding sentence uses *the year's selection* to mean the year's rows before narrowing —
"narrowing, sorting and counting apply to the whole year's selection" — so this is the consistent
reading, and it keeps the lists stable across a filter change and answerable in one round trip.

**The residual is accepted and stated rather than discovered**: with *both* filters chosen an empty
list is reachable, and R23's promise strictly holds one filter at a time. Cross-filtering each list
against the *other* filter would close it, and the volumes would afford it — but it doubles the
reads, reshuffles the chooser under the reader's hand on every interaction, and buys a guarantee the
requirement does not make. `LicitacionsSelection` already admits the change if it turns out to
matter.

**Out of scope:** the section type and the use cases (TASK-0005), and any HTTP shape
([TASK-0007](TASK-0007-the-licitacions-read-endpoints.md)).

## Acceptance criteria

Integration-tested against PostgreSQL, over a fixture whose Órgano holds licitacións in several
years, several CPV codes and several states:

- The years read back are exactly those with **visible** licitacións, **newest first**, and a
  licitación with **no publication date** contributes **no year** — in particular no null offered as
  one. ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #32, #36)
- An Órgano all of whose licitacións are withdrawn, or all of which are undated, yields **no years at
  all**, which is what makes it indistinguishable from one holding none. (SPEC-0008 #36, #37)
- The CPV facets are exactly the codes the year's visible licitacións carry — **including** one
  carried only against a procedure that has lotes, and one carried only on a lote — and exclude every
  code carried solely by a withdrawn classification, a withdrawn licitación, an undated one, or a
  licitación of another year. A code the `cpv` table holds but no licitación of the year carries is
  **not offered**. (SPEC-0008 #33)
- Choosing any offered CPV code yields a non-empty page from
  [TASK-0003](TASK-0003-paged-ordered-counted-reads.md)'s read — the property R23 asks the rule to
  guarantee, asserted rather than assumed. The same holds for every offered state. (SPEC-0008 #33)
- Two states sharing one label — codes **101 and 102**, both *Histórico* — are **two** facet entries,
  and neither absorbs the other. This is the assertion that fails if the `GROUP BY` drops the code.
  (SPEC-0008 #33)
- State labels differing only by an accent order as Galician, among their unaccented neighbours
  rather than after `Z`. *(A design choice of this feature's — **no criterion**: #33 governs which
  values are offered, never their order.)*
- A state whose **label** the source never published, and a CPV whose **description** it never
  published, are both returned — under their codes, ordered last — rather than dropped or rendered as
  blanks. ❗ The case a fully populated fixture never reaches. (SPEC-0008 #33)
- The facets are unaffected by the **other** filter being set — asserted in both directions.

  > ⚠️ **This freezes a reading R23 does not settle.** "*Only codes and states the year's selection
  > actually contains*" is ambiguous between *the year's rows before narrowing* and *as currently
  > narrowed*; the feature argues the first from R23's preceding sentence, and this test makes it
  > binding. **The clean fix is to amend R23** to say which it means, rather than leaving a later
  > reader to find a test asserting the opposite of their reading. Raised in the README's
  > candidate-criteria section.
