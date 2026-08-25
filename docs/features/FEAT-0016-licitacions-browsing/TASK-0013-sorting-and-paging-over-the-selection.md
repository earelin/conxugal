---
feat: FEAT-0016
domain: frontend
adrs: [0004, 0015, 0022]
status: todo
depends_on: [TASK-0011, TASK-0012]
---

# Sorting and paging over the whole selection

R23's two sorts in both directions, R20's paging control, and the one rule that keeps them coherent.

## Scope

- **The four orderings** — publication date and amount, ascending and descending — offered as **one
  control of four entries** rather than sortable column headers. R23 fixes a closed set that cannot
  grow without the requirement changing, and a four-option control cannot express anything outside it;
  clickable headers would suggest a dynamic sort the design forecloses, and only two of the columns
  would respond.

  **Sorting by amount orders each row by the figure it states** (R24) — so an unawarded row is placed
  by its **budget**, among rows placed by their **awards**. That is accepted because an ordering makes
  no claim of comparability the way a sum does: each row still says which figure put it there. What is
  forbidden is adding them, and nothing here adds anything.
- **The paging control is `shared/ui/Pagination`, taken unchanged.** R20 cites SPEC-0005 R17's control
  rather than redefining it, and that component already reads
  [ADR-0022](../../architecture/0022-paged-collection-contract-from-micronaut-data.md)'s envelope
  directly — `page`, `size`, `totalItems`, `totalPages` — with no conversion and no arithmetic between
  the wire and the control.

  ❗ **It is reused, not copied and not extended.** R17's stated reason is that *"a reader meets
  several of these lists in one session, and one of them paging differently from the rest is a defect
  they would experience as inconsistency rather than as a design"*. A licitacións-specific paging
  control would be exactly that defect.
- **The whole selection lives in the URL query string**, spelled exactly as the API takes it:
  `?year=2025&cpv=45000000&state=4&sort=amount,desc&page=3`. Because ADR-0022's `page` is 1-based, the
  number in the URL, the number the API takes and the number the control shows are **one number**.
- **One rule, held in one place**: any write to `year`, `cpv`, `state` or `sort` **drops `page`**.
  Four controls, one transition, and a reader can never be left on a page number that no longer means
  what it did. Held as component state instead, the rule would have to be remembered at each control —
  which is the defect this arrangement exists to prevent.
- **A page beyond the last** is answered by the server with an empty page carrying the true total, and
  the client **clamps to the last page** rather than showing an error. Reachable from a stale shared
  link, or from an import that stored rows between two requests.
- **The count is of the selection**, not of the page, and the response states no ordering — the URL
  does, and the client is authoritative on it, so a shared link is read back by the same parser that
  wrote it.

**Out of scope:** any change to `shared/ui/Pagination`, `PageJump` or `askedPage`; keyset paging,
which ADR-0022 fixes as positional and which is that record's business rather than a task's; and any
fifth ordering.

## Acceptance criteria

- The list pages through **exactly** the number of licitacións the selection states — first, previous,
  next, last and a chosen page — with **none repeated and none skipped**, and the two ends disabled at
  the two ends rather than hidden.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #28)
- The list is ordered by **publication date descending with the publication identifier descending as
  tie-break** by default, so two procedures published on the same date have a determinate order; and
  **every ordering R23 offers is likewise total**, with paging over any of them repeating and skipping
  nothing. (SPEC-0008 #30)
- **Sorting and counting apply to the whole year's selection**: the first page after sorting by amount
  descending holds the **highest-amount licitación of the year**, not merely of the page previously
  displayed. (SPEC-0008 #34)
- **A sort by amount places each row by the figure it states** — an unawarded row by its budget, an
  awarded one by its award — and a row that states no figure is placed last in **both** directions.
  (SPEC-0008 #35)
- **Applying or clearing any part of the selection returns the reader to the first page**; moving
  between pages changes neither the count, the page total nor the ordering. (SPEC-0008 #34)
- A **shared URL** carrying a year, both filters, a sort and a page reproduces exactly that view, and
  the browser's back button walks the paging history. (SPEC-0008 #34)
- A **page beyond the last** clamps to the last page rather than erroring, and the count shown is the
  selection's true total throughout. (SPEC-0008 #28)
- `shared/ui/Pagination` is used **unmodified**, and its existing component tests and the contratos
  menores acceptance specs pass unchanged — asserted by the diff touching none of those files.
- The acceptance spec drives the control by its accessible roles and Galician names, runs serially
  against the shared WireMock stub, and adds its mappings to `ui/wiremock/mappings/` beside the
  existing ones.
