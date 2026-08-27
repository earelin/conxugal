---
feat: FEAT-0016
domain: frontend
adrs: [0003, 0004, 0015, 0018, 0022]
status: todo
depends_on: [TASK-0010, TASK-0011]
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

**The orderings themselves are the server's** and are proved against PostgreSQL by
[TASK-0003](TASK-0003-paged-ordered-counted-reads.md); what is asserted here is what the client
**sends**, **renders** and **remembers**.

- ❗ **The sort control offers exactly four entries and can express nothing else** — publication date
  and amount, ascending and descending — sending the API's own spelling (`amount,desc`) verbatim. It
  is **one control**, not sortable column headers: R23 fixes a closed set, and a header affordance
  would suggest a dynamic sort the design forecloses on two of six columns. This is the task's
  headline scope item and had no criterion in an earlier draft.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #34)
- The section requests the **default ordering** when the URL states none, and the URL is authoritative
  when it states one — a shared link is read back by the same parser that wrote it. (SPEC-0008 #30)
- The list pages through **exactly** the number of licitacións the envelope states — first, previous,
  next, last and a chosen page — with **none repeated and none skipped**, and the two ends **disabled,
  not hidden**, at the two ends. (SPEC-0008 #28)
- **Choosing a sort puts the reader on page 1**, through
  [TASK-0009](TASK-0009-year-chooser-and-section-state.md)'s module — asserted as *the control writes
  through the module*, since the rule itself is proved there. Moving between **pages** writes neither
  the sort nor either filter. (SPEC-0008 #34 re-page half)
- ❗ **The table renders the server's order, unaltered.** Given a page whose rows arrive in an order
  the client did not choose, the table draws them in **that** order and applies no sort of its own.

  This is the live defect nothing else in the feature catches: a table that re-sorts the rows it holds
  looks correct on page 1 of every ordering and is wrong on every page after it, because the server
  ordered the whole selection and the client only ever holds fifty rows of it. *That the whole
  selection is ordered and counted is [TASK-0003](TASK-0003-paged-ordered-counted-reads.md)'s; that
  the client does not undo it is this task's, and it is the half the feature's own task list assigns
  here.* (SPEC-0008 #34 whole-selection half)
- **A row whose amount is `UNSTATED` is drawn wherever the server put it** — the client neither lifts
  it nor sinks it. The `NULLS LAST` placement is the statement's; not fighting it is the table's.
  (SPEC-0008 #35 sort half)
- **The count shown is the envelope's `totalItems`**, unchanged as the reader pages — never the length
  of the rows in hand, which is the page size on every page but the last. (SPEC-0008 #28)
- A **shared URL** carrying a year, both filters, a sort and a page reproduces exactly that view, and
  the browser's back button walks the paging history. (SPEC-0008 #34)
- A **page beyond the last** clamps to the last page rather than erroring, and the count shown is the
  selection's true total throughout. (SPEC-0008 #28)
- `shared/ui/Pagination` is used **unmodified**, and its existing component tests and the contratos
  menores acceptance specs pass unchanged — asserted by the diff touching none of those files.
- The acceptance spec drives the control by its accessible roles and Galician names, runs serially
  against the shared WireMock stub, and adds its mappings to `ui/wiremock/mappings/` beside the
  existing ones.
