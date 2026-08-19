---
feat: FEAT-0011
domain: frontend
adrs: [0003, 0004, 0015, 0018, 0022]
status: done
depends_on: [TASK-0007, TASK-0008, TASK-0010]
---

# Sorting and paging over the selection

The two sorts in both directions, [TASK-0008](TASK-0008-shared-paging-control.md)'s control wired
to the list, the whole selection held in the URL query string in the API's own spelling, and the
one rule that a change to the selection drops the page. It completes the section, and it is where
the feature's acceptance journeys are proved.

## Scope

- **The ordering control**: one Mantine `Select` of **four entries** — publication date newest
  first, publication date oldest first, amount largest first, amount smallest first — writing
  `?sort=publicationDate,desc` and its three siblings, spelled exactly as the API takes them.
  - **Not sortable column headers.** R19 offers a closed set, and a four-option control cannot
    express anything outside it — the same closure the four statements have on the server. Headers
    were the obvious alternative and are deliberately not built: only two of six columns would
    respond, and the affordance would suggest a dynamic sort the design forecloses.
- **The paging control wired to the list**, taking `page`, `size`, `totalItems` and `totalPages`
  straight from the response envelope and writing `?page=N`. Because
  [ADR-0022](../../architecture/0022-paged-collection-contract-from-micronaut-data.md)'s `page` is
  1-based, **the number in the URL, the number the API takes and the number the control shows are
  one number** — nothing in the app converts between bases.
- **The selection lives in the URL, in one place.** `year`, `sort` and `page` are read and written
  through a single helper in the slice, and that helper holds R17's re-page rule: **any write to
  `year` or `sort` drops `page`**. Held as component state instead, the rule would have to be
  remembered at every control. The family is the **path**, not a parameter, so switching tab
  discards all three along with the route — they describe a selection that no longer exists.
- **A page past the end**: the API answers an empty page carrying the true total, and the client
  **clamps to the last page** rather than showing an error. Reachable by a stale shared link or by
  an import that stored rows between two requests. The clamp is the client's precisely because
  clamping server-side would make the response disagree with the request that produced it.
- **The response does not state which ordering it applied** — the envelope carries no sort — so the
  URL is authoritative, and a shared link is read back by the same parser that wrote it. An
  unparseable `sort` in a pasted URL falls back to the default rather than being sent on to be
  refused.
- **Acceptance journeys** against the stubbed API
  ([ADR-0018](../../architecture/0018-frontend-acceptance-tests-against-a-stubbed-api.md)), with
  stubs added under `ui/wiremock/mappings/` and specs under `ui/acceptance/specs/`. The hard cases
  are cheap here and none of them needs a million rows:
  - a year whose count is **not a multiple of the page size**, walked end to end;
  - a **partial** section, and one that says the Órgano is no longer updated;
  - an Órgano whose only contracts are anomalous, and one whose visible count differs from what it
    holds — proved by the count the stub states, which the reader has no other view of;
  - changing the year and the sort while deep in a selection.
  Specs drive accessible roles and the Galician copy of `strings.ts`, and must not assert on
  locale-formatted dates or amounts.

## What building it found

> **The paging control had to write the page itself, not report it upwards.** The obvious
> shape was one component owning every write to the query string and the list emitting a
> page for it to write. The clamp forecloses it: a page past the end is only known once the
> response is in hand, and a callback fired from a render is not a thing React allows — so
> the list would have needed an effect where the year correction already had a
> `<Navigate replace>`. The list therefore writes `page` and the section writes `year` and
> `sort`, both through `selection.ts`. *One place* turned out to mean one **module**, not
> one component, and the re-page rule lives there rather than at either call site.
>
> **`chosenYear` moved out of `summary.ts`.** It reads the URL, which is this task's
> subject; what stayed behind is the narrowing of the outlet context. Splitting it is what
> let `respelling` treat all three parameters by one rule instead of the year having its own.
>
> **The correction generalised further than expected, and one case had to be excluded.**
> `?year=2019&page=0` corrects both — but the year correction *drops* the page rather than
> respelling it, so respelling it in the same write would immediately undo that. The
> exclusion is stated in `respelling` rather than left for the reader of `withSelection` to
> notice.
>
> **The stub could not be faithful and walkable at once.** The contract's default `size` is
> 50 and the client sends none, so a stub with more than one page needs a hundred hand-written
> contracts. `wiremock/README.md` records the three-entry page as a deliberate simplification
> rather than leaving the next reader to find the contract says otherwise.
>
> **Paging cannot unmount the control that does the paging.** The first build let the
> list fall to its loading state on every page, which took the stated count and the page
> total off screen with it and dropped keyboard focus from the button just pressed — the
> opposite of what [`design/section-states.svg`](design/section-states.svg)'s loading panel
> says: *os controis non se moven … só se move a xanela sobre a selección*. The page already
> read is now held while the next is fetched, dimmed and marked busy. **The two cases named
> for that criterion could not have caught it**: both asserted the steady state on either
> side of the transition, so they passed with the control absent in between. They now assert
> during it.
>
> **The clamp reads the page that was asked for, not the one the answer echoes.** ADR-0022
> guarantees the echo, but reading the request instead costs nothing and leaves the URL and
> the control unable to disagree whatever comes back. It is also skipped while a held-over
> answer is on screen, that answer knowing nothing about the new selection's page count.
>
> **`chosenPage` had to bound the page above as well as below.** The API's `page` is an
> `int32`, so `?page=9999999999` is a 400 — the error state, which is precisely what the
> clamp exists to keep a stale link out of. A page past the end of the *selection* is still
> left alone: that one the API answers.
>
> **Holding the page already read had to be scoped to the selection.** react-query's
> `keepPreviousData` is `(previous) => previous` and compares no keys, so it holds an answer
> across a change of year or ordering too — and there the count, the page total and the page
> in force all change, leaving the control stating the old selection's numbers with nothing
> saying they were stale, and its jump box bounding a typed page by the wrong total. The hold
> is now a key comparison: within one year and ordering the window moves, and a change of
> selection is an ordinary wait.
>
> **Keeping focus on the pressed button removed the only thing a screen reader heard.** The
> arrival used to be announced by the list remounting into `LoadingIndicator`'s `role="status"`;
> holding the control still deletes that, and `aria-busy` is not announced — it only quietens a
> region that is already live. The section now says which page a reader is on, through
> `aria-live` rather than `role="status"` so it is not counted among the two statements the
> section makes about itself.
>
> **A respelling is not a change, and only a change may drop the page.** `?year=02025&page=5`
> is the same year written differently, and routing the correction through the re-page rule
> returned the reader to page 1 for no reason. The corrections are written directly now, so
> the rule applies to a year that is genuinely different and not to one spelled another way.
>
> **The end of a walk disables the control that got there**, and a disabled element cannot
> hold focus — so the browser dropped a keyboard reader to the top of the document from a
> button they pressed on purpose. `shared/ui/Pagination` moves focus to the jump box between
> the two pairs. It is the one edit this task makes to
> [TASK-0008](TASK-0008-shared-paging-control.md)'s control, and it varies nothing the control
> promises: the two ends are still disabled rather than hidden. **jsdom does not blur a
> disabled element**, so the case is an acceptance one — no component test could observe it.
>
> **A test that waits can pass on the state it exists to reject.** Playwright's web-first
> assertions retry for five seconds, so the case that asserted the control was still on screen
> during a 700 ms delay would have waited out a control that unmounted for the whole fetch. The
> request is held open until the test lets go, which makes the in-flight window a state the
> test owns rather than a race it has to win.
>
> **At 360 px the ordering's entries are longer than the line they get.** They are whole
> Galician sentences; an input clips rather than wraps, so the chosen entry read as a
> different one. The control now ellipsises. The acceptance suite's `toBeInViewport` check
> also had to scroll the paging control into view first — at that height it sits below the
> fold, and the assertion was reporting that rather than the width it is about.

## Acceptance criteria

- The ordering control offers exactly four entries, and each produces the ordering it names —
  amount descending puts the largest contract of the **whole year** on the first page.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #28)
- Paging a year whose count is not a multiple of the page size walks every contract exactly once,
  ending on a short last page, with **none repeated and none skipped**. (SPEC-0005 #23)
- Moving between pages changes neither the stated entry count, the page total nor the ordering.
  (SPEC-0005 #24, #28)
- Changing the year, the sort key or the direction returns the reader to page 1, and does so from
  every control that can change them. (SPEC-0005 #24)
- `year`, `sort` and `page` are all in the URL query string in the API's spelling; a copied URL
  reopens the same page of the same selection in the same ordering, and the browser's back button
  walks paging history. (SPEC-0005 #23, #24)
- The page number in the URL, the one sent to the API and the one displayed are identical — no
  conversion appears anywhere in the slice. (SPEC-0005 #23)
- A URL naming a page past the end lands the reader on the last page with the true count stated,
  not on an error and not on an empty table. (SPEC-0005 #23)
- Acceptance specs cover the four journeys above and pass against the WireMock stub through
  `npm run test:acceptance`.
- `npm run lint`, `npm run build` and `npm test` pass from `ui/`.
