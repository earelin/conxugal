---
feat: FEAT-0011
domain: frontend
adrs: [0003, 0004, 0015, 0018, 0022]
status: todo
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
