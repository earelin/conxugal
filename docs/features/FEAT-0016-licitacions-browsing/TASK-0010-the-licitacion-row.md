---
feat: FEAT-0016
domain: frontend
adrs: [0003, 0004, 0015, 0018]
status: todo
depends_on: [TASK-0008, TASK-0009]
---

# The licitación row, and the read behind it

What R20 puts on a row, what R24 and R7 make it say about the figure it shows — **and the read that
fetches the rows**, which nothing before this task performs.

## Scope

- **The list read**: the hook and types for `GET /api/organo/{id}/licitacions`, keyed on the selection
  so a held page can never survive a change of year, filter or ordering — the `placeholderData`
  key-prefix comparison `features/contratos-menores/contracts.ts` already uses — plus the **loading
  and error states**, the `aria-busy` dimming, and keeping the last good envelope so a failed read
  does not tear the control off the page.

  FEAT-0011's counterpart task is titled *the contract row, **and the read behind it*** for this
  reason: a row component with no read is a component nobody can see, and an earlier draft of this
  feature left the read unowned between three tasks.
- **Its WireMock mappings**, in `ui/wiremock/mappings/`, added here rather than with the paging task.
  ❗ Without them the section renders its **error state** in dev, preview and every e2e run from the
  moment [TASK-0008](TASK-0008-licitacions-section-slice-and-route.md)'s route exists — the shared stub
  is what those three environments read, and a route with no mapping is a 404 to the SPA.

  ❗ **`organo.json` needs new fixtures too, and nothing else claims them.** Its five Órganos carry
  `contratosMenores` **only** — there is no two-family Órgano and no licitacións-only one, which are
  exactly the two this task's and TASK-0008's criteria turn on. Adding them is additive: every
  existing Órgano keeps its shape, so the shipped contratos menores specs are untouched.
- **The columns** — the publication identifier, the publication date, the object, the **state**, the
  **amount**, and the **awardee or the count of them** — plus a link to the publication at the
  official source.

  **No column states the awarding Órgano** (#28): every row of this list belongs to the Órgano already
  open. And **no column carries an expediente, an estimated value, a type or a lote count** — those
  are R21's page, and a row that carried them would be a detail view drawn as a table.
- **The amount, with what it is.** R24 gives three cases and the row must say which it is showing:
  - **awarded** — the awarded amount, or the sum of the lotes awarded so far;
  - **partly awarded** — the awarded sum, **marked as covering part of the procedure**. Not the
    budget, which the awards have already partly superseded, and not a mixture of the two, which
    would be a figure nothing published;
  - **nothing awarded** — the **base budget, labelled as a budget**, so the row says something about
    the size of what is being tendered rather than nothing.

  A row whose basis is `UNSTATED` states **no figure** — it is shown as absent, never as `0`, never as
  a placeholder standing in for a number.

  ❗ **The two are never presented as the same figure.** The server sends one value and the basis that
  names it, so the row renders what it was given; what this task must not do is compute, combine or
  substitute either.
- **The VAT label.** The base budget **includes VAT** and is labelled so wherever it appears (R7, #8),
  on the same rule SPEC-0005 R7 imposes on the contrato menor amount and for the same reason: an
  unlabelled figure invites exactly the wrong comparison. The estimated value, which excludes VAT,
  never reaches a row — which is why a row carries one label rather than a rule about two.
- **The state**, shown as the source's own label. ❗ Two states may share one label — codes 101 and
  102 are both *Histórico* — so the row must not treat the label as an identity, and the filter
  control that pairs with it works on the code
  ([TASK-0011](TASK-0011-cpv-and-state-filters.md)). ❗ **And the label may be absent**:
  `licitacion_state.label` is nullable, so a state the source published without one is shown by its
  **code** rather than as a blank cell.
- **The awardee, or how many.** A row whose procedure has **exactly one** awardee names it — under the
  operador's own selected name, with its fiscal identifier where it holds one — and **offers a route**
  to that operador. One whose lotes went to **more than one** states **how many** rather than picking
  one of them.

  ❗ **A row whose award resolved to nobody names nobody.** The count is zero, and no name is rendered
  from any source. This family holds **no per-row name at all** (#24), and the published
  `awardee_name` is a resolution input the API does not send — see
  [TASK-0003](TASK-0003-paged-ordered-counted-reads.md), which corrects V19's comment claiming
  otherwise.

  ❗ **An awardee may hold no fiscal identifier.** An unidentified consortium is an operador like any
  other and "offers a route like any other — what it lacks is a fiscal identifier to show beside its
  name, not a page to open" (R20). The row shows the name without the identifier, and does **not**
  fall back to hiding the awardee.
- **The route's target does not exist yet.** R21's licitación page is the next feature's, so the row
  is built with its crossing **named and unbuilt** — the shape FEAT-0011 accepted for its operador
  crossing. What this task must not do is render a control that goes nowhere: until that page exists
  the row states its awardee and its count without offering a dead link.

**Out of scope:** the filters, sorting and paging (tasks 12 and 13), R21's page, and the crossing into
an operador, which SPEC-0006's read feature owns.

## Acceptance criteria

- A row whose procedure has exactly **one** awardee names it; one whose lotes went to **more than
  one** states **how many**; one whose award resolved to **nobody** names nobody and states none.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #20, #29)
- An awardee that is a consortium the source did **not** identify is named, with **no** fiscal
  identifier shown beside it, and is not hidden or rendered as unresolved. (SPEC-0008 #20 unidentified-consortium half)
- **No row renders any name for a party the API did not resolve** — asserted against a stub whose
  award carries a count of zero, so the assertion fails if a later change reaches for a published
  name. (SPEC-0008 #24)
- ❗ **The row renders the `basis` it was given and computes nothing.** Given `AWARDED` it states the
  value; given `AWARDED` with `partial` it states the value **marked as covering part of the
  procedure**; given `BUDGET` it states the value **labelled as a budget**; given `UNSTATED` it states
  **no figure** — not a zero, not an em dash standing in for one.

  *Which* figure a procedure gets, and *whether* it is partial, is
  [TASK-0003](TASK-0003-paged-ordered-counted-reads.md)'s aggregate arithmetic, proved against
  PostgreSQL. A stub makes any of it true by construction, so what is assertable here is that the
  three cases render differently and that the row invents none of them. (SPEC-0008 #35)
- Wherever the base budget is shown it is labelled **VAT-inclusive**. #8's other half — an estimated
  value labelled VAT-exclusive — never reaches a row and is the R21 feature's.
  (SPEC-0008 #8 budget half)
- A row carrying a **state and no awardee** renders its state and names nobody — the shape an
  undecided procedure arrives in. *(That such a procedure is **shown at all** is the visibility
  predicate's, proved in TASK-0003; a stub cannot withhold what it was asked to return.)*
  (SPEC-0008 #36 display half)
- Each row offers a route to the corresponding publication **at the official source**, with an
  accessible name identifying which licitación it opens, and **no row names the awarding Órgano**.
  (SPEC-0008 #28)
- Every value is rendered **exactly as stored** — no truncation, case folding or reformatting the row
  invents — save the locale rendering of dates and decimals, which is presentation. A long `obxecto`
  wraps; an absent one is shown as absent. *(R33's display rule. **No criterion** — SPEC-0008 has no
  analogue of SPEC-0005 #40, and #44 is wholly storage. See the README's candidate-criteria table.)*
- A state whose **label the source never published** is shown by its **code**, not as an empty cell.
  *(A rendering choice of this feature's — **no criterion**: #33 governs which values are offered as
  filters, not how a row draws one.)*
- The section **renders rows** for an Órgano holding licitacións and no contratos menores, end to end
  against the stub — which is the half of #26 the visible-set bean cannot prove.
  (SPEC-0008 #26 viewable half)
- A read that **fails** leaves the last good page and its control on the page rather than tearing them
  off, and a read **in flight** dims rather than blanks.
- Acceptance specs identify rows by their **publication identifier**, never by a locale-formatted date
  or amount, on the shipped `contratos-menores.spec.ts` convention.
