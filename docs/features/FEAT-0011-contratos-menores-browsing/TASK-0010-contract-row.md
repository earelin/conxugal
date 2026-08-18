---
feat: FEAT-0011
domain: frontend
adrs: [0003, 0004, 0015]
status: done
depends_on: [TASK-0007, TASK-0009]
---

# The contract row, and the read behind it

The section's single request and the table it fills: every attribute the system holds, on one row,
because a contrato menor has **no detail view** to click into. Drawn field by field in
[`design/row-anatomy.svg`](design/row-anatomy.svg) and in place in
[`design/section.svg`](design/section.svg).

## Scope

- **The slice's one read**: `GET /api/organo/{id}/contratos-menores?year=…` through
  `apiFetch` and a react-query `useQuery`, keyed on the whole selection so a change of year is a
  different query rather than a mutation of one. The default ordering and page 1 are used here;
  the sort control and the paging wiring are
  [TASK-0011](TASK-0011-sorting-and-paging-over-the-selection.md)'s.
- **A six-column table**, whose widths follow how the values are read: the date with the source
  identifier beneath it, `obxecto` given the width to wrap, the awardee with its fiscal identifier
  beneath, the amount right-aligned and bold, the duration, and the source link.
- **What each row carries**, all of it R16's:
  - the publication date and, dimmed and monospaced beneath it, the `sourceId` — an identifier a
    reader copies rather than reads, and the tiebreaker that makes the ordering total;
  - the `obxecto`, **exactly as stored**: no truncation, no case folding, no reformatting the row
    invents. A long one wraps;
  - the **awardee as text** — the operador's selected name and its canonical fiscal identifier.
    **Not a link**: the operador route belongs to
    [SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) R8's read feature, and a link to a
    route that 404s is worse than none. That feature adds the crossing to this row;
  - the amount, under a two-line `IMPORTE / IVE INCLUÍDO` header. **The VAT label is not
    optional**: the thresholds that define a contrato menor are VAT-exclusive, so an unlabelled
    figure invites exactly the wrong comparison — and any total derived from one carries the label
    too, wherever one appears;
  - the duration, under a column marked unreliable — an `ⓘ` on the header and a caption beneath
    the table saying the source frequently publishes a per-Órgano default rather than a
    per-contract value. **On the column, not on every row**: one statement covers every row and
    reads once;
  - the `sourceUrl` as an **icon-only link** to the publication at the official source, which
    therefore needs an `aria-label` naming the contract it opens. A drawing cannot show that and
    this task must not skip it.
- **No row states the awarding Órgano.** Every row of this list belongs to the Órgano already open.
- **No placeholder for a date, an amount or an awardee, and no code path that would produce one.**
  A row that reaches the client carries all three, because R28 withholds one that does not — so
  there is no absent-value rendering to write for them, and writing one would be writing for a
  case that cannot occur. Never a `0`, never an em dash, never a *sen datos*.
- **`obxecto` and `duration` can still be absent**, because the source leaves them blank and
  FEAT-0009 stores that as null. They are the only two, and they are **shown as absent** — a
  dimmed marker in the cell — never invented, inferred or filled from a neighbouring row.
- **Date and amount formatting is presentation, not data**: `12 mar 2025` and `12.480,00 €`, the
  abbreviated Galician month following FEAT-0007's precedent. R27's *exactly as stored* governs
  the text the source publishes — `obxecto`, the awardee's name, the duration string — and those
  are rendered verbatim. Any formatter **pins its own separators** rather than trusting the
  runtime's `gl-ES` data, as `metricsFormat.ts` does, because the acceptance suite runs across
  browser builds that disagree.
- **Loading and failure states**: the shared `LoadingIndicator` while the page is in flight and
  the shared `ErrorAlert` with a retry when the read fails — the section's frame and its two
  statements stay rendered around them.
- **Two absences a reader will notice, both deliberate**: no CPV filter and no free-text search
  over `obxecto`. Neither is hidden, disabled or coming soon; there is simply no control.
- Component-tested with Vitest and Testing Library, HTTP mocked at the network boundary with
  `nock` per the project's convention.

## Acceptance criteria

- A row states the source identifier, the publication date, the `obxecto`, the amount, the
  duration, the awardee's name and canonical fiscal identifier, and offers a link to the
  publication at the official source.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #9 display half, #16
  display half, #25 source half, #39 awardee-name half)
- The amount column is labelled as including VAT, and so is any total derived from one.
  (SPEC-0005 #10)
- The duration column carries an indication that the source frequently publishes a per-Órgano
  default rather than a per-contract term. (SPEC-0005 #41)
- The awardee is rendered as **text**, with no link and no operador identifier; the row states no
  awarding Órgano. (SPEC-0005 #11 display half, #21)
- `obxecto`, the awardee's name and the duration string render exactly as returned — not
  truncated, case-folded or reformatted — and a long `obxecto` wraps. (SPEC-0005 #40)
- No row renders a placeholder, dash or zero for a date, an amount or an awardee, and no code path
  exists that would; an absent `obxecto` or `duration` — the only two that can be absent — renders
  as absent rather than as invented text. (SPEC-0005 #11, #42 display half)
- The source link is reachable by an accessible name that identifies the contract it opens.
- The section renders **no CPV filter and no free-text search control**, disabled or otherwise.
  (SPEC-0005 #27)
- A failed read shows the shared error state with a retry, and does not blank the section's own
  statements.
- Copy is Galician, lives in `strings.ts`, and the table is usable at a 360 px viewport.
  ([SPEC-0001](../../specs/SPEC-0001-web-ui.md) AC6, AC7)
- `npm run lint`, `npm run build` and `npm test` pass from `ui/`.
